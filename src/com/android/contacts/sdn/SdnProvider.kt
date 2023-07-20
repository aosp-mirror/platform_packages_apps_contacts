/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.sdn

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context.TELECOM_SERVICE
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.RawContacts
import android.telecom.TelecomManager
import android.util.Log
import com.android.contacts.R

/** Provides a way to show SDN data in search suggestions and caller id lookup. */
class SdnProvider : ContentProvider() {

  private lateinit var sdnRepository: SdnRepository
  private lateinit var uriMatcher: UriMatcher

  override fun onCreate(): Boolean {
    Log.i(TAG, "onCreate")
    val sdnProviderAuthority = requireContext().getString(R.string.contacts_sdn_provider_authority)

    uriMatcher =
      UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(sdnProviderAuthority, "directories", DIRECTORIES)
        addURI(sdnProviderAuthority, "contacts/filter/*", FILTER)
        addURI(sdnProviderAuthority, "data/phones/filter/*", FILTER)
        addURI(sdnProviderAuthority, "contacts/lookup/*/entities", CONTACT_LOOKUP)
        addURI(
          sdnProviderAuthority,
          "contacts/lookup/*/#/entities",
          CONTACT_LOOKUP_WITH_CONTACT_ID,
        )
        addURI(sdnProviderAuthority, "phone_lookup/*", PHONE_LOOKUP)
      }
    sdnRepository = SdnRepository(requireContext())
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? {
    if (projection == null) return null

    val match = uriMatcher.match(uri)

    if (match == DIRECTORIES) {
      return handleDirectories(projection)
    }

    if (
      !isCallerAllowed(uri.getQueryParameter(Directory.CALLER_PACKAGE_PARAM_KEY)) ||
      !sdnRepository.isSdnPresent()
    ) {
      return null
    }

    val accountName = uri.getQueryParameter(RawContacts.ACCOUNT_NAME)
    val accountType = uri.getQueryParameter(RawContacts.ACCOUNT_TYPE)
    if (ACCOUNT_NAME != accountName || ACCOUNT_TYPE != accountType) {
      Log.e(TAG, "Received an invalid account")
      return null
    }

    return when (match) {
      FILTER -> handleFilter(projection, uri)
      CONTACT_LOOKUP -> handleLookup(projection, uri.pathSegments[2])
      CONTACT_LOOKUP_WITH_CONTACT_ID ->
        handleLookup(projection, uri.pathSegments[2], uri.pathSegments[3])
      PHONE_LOOKUP -> handlePhoneLookup(projection, uri.pathSegments[1])
      else -> null
    }
  }

  override fun getType(uri: Uri) = Contacts.CONTENT_ITEM_TYPE

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    throw UnsupportedOperationException("Insert is not supported.")
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
    throw UnsupportedOperationException("Delete is not supported.")
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int {
    throw UnsupportedOperationException("Update is not supported.")
  }

  private fun handleDirectories(projection: Array<out String>): Cursor {
    // logger.atInfo().log("Creating directory cursor")

    return MatrixCursor(projection).apply {
      addRow(
        projection.map { column ->
          when (column) {
            Directory.ACCOUNT_NAME -> ACCOUNT_NAME
            Directory.ACCOUNT_TYPE -> ACCOUNT_TYPE
            Directory.DISPLAY_NAME -> ACCOUNT_NAME
            Directory.TYPE_RESOURCE_ID -> R.string.sdn_contacts_directory_search_label
            Directory.EXPORT_SUPPORT -> Directory.EXPORT_SUPPORT_NONE
            Directory.SHORTCUT_SUPPORT -> Directory.SHORTCUT_SUPPORT_NONE
            Directory.PHOTO_SUPPORT -> Directory.PHOTO_SUPPORT_THUMBNAIL_ONLY
            else -> null
          }
        },
      )
    }
  }

  private fun handleFilter(projection: Array<out String>, uri: Uri): Cursor? {
    val filter = uri.lastPathSegment ?: return null
    val cursor = MatrixCursor(projection)

    val results =
      sdnRepository.fetchSdn().filter {
        it.serviceName.contains(filter, ignoreCase = true) || it.serviceNumber.contains(filter)
      }

    if (results.isEmpty()) return cursor

    val maxResult = getQueryLimit(uri)

    results.take(maxResult).forEachIndexed { index, data ->
      cursor.addRow(
        projection.map { column ->
          when (column) {
            Contacts._ID -> index
            Contacts.DISPLAY_NAME -> data.serviceName
            Data.DATA1 -> data.serviceNumber
            Contacts.LOOKUP_KEY -> data.lookupKey()
            else -> null
          }
        },
      )
    }

    return cursor
  }

  private fun handleLookup(
    projection: Array<out String>,
    lookupKey: String?,
    contactIdFromUri: String? = "1",
  ): Cursor? {
    if (lookupKey.isNullOrEmpty()) {
      Log.i(TAG, "handleLookup did not receive a lookup key")
      return null
    }

    val cursor = MatrixCursor(projection)
    val contactId =
      try {
        contactIdFromUri?.toLong() ?: 1L
      } catch (_: NumberFormatException) {
        1L
      }

    val result = sdnRepository.fetchSdn().find { it.lookupKey() == lookupKey } ?: return cursor

    // Adding first row for name
    cursor.addRow(
      projection.map { column ->
        when (column) {
          Contacts.Entity.CONTACT_ID -> contactId
          Contacts.Entity.RAW_CONTACT_ID -> contactId
          Contacts.Entity.DATA_ID -> 1
          Data.MIMETYPE -> StructuredName.CONTENT_ITEM_TYPE
          StructuredName.DISPLAY_NAME -> result.serviceName
          StructuredName.GIVEN_NAME -> result.serviceName
          Contacts.DISPLAY_NAME -> result.serviceName
          Contacts.DISPLAY_NAME_ALTERNATIVE -> result.serviceName
          RawContacts.ACCOUNT_NAME -> ACCOUNT_NAME
          RawContacts.ACCOUNT_TYPE -> ACCOUNT_TYPE
          RawContacts.RAW_CONTACT_IS_READ_ONLY -> 1
          Contacts.LOOKUP_KEY -> result.lookupKey()
          else -> null
        }
      }
    )

    // Adding second row for number
    cursor.addRow(
      projection.map { column ->
        when (column) {
          Contacts.Entity.CONTACT_ID -> contactId
          Contacts.Entity.RAW_CONTACT_ID -> contactId
          Contacts.Entity.DATA_ID -> 2
          Data.MIMETYPE -> Phone.CONTENT_ITEM_TYPE
          Phone.NUMBER -> result.serviceNumber
          Data.IS_PRIMARY -> 1
          Phone.TYPE -> Phone.TYPE_MAIN
          else -> null
        }
      }
    )

    return cursor
  }

  private fun handlePhoneLookup(
    projection: Array<out String>,
    phoneNumber: String?,
  ): Cursor? {
    if (phoneNumber.isNullOrEmpty()) {
      Log.i(TAG, "handlePhoneLookup did not receive a phoneNumber")
      return null
    }

    val cursor = MatrixCursor(projection)

    val result = sdnRepository.fetchSdn().find { it.serviceNumber == phoneNumber } ?: return cursor

    cursor.addRow(
      projection.map { column ->
        when (column) {
          Contacts.DISPLAY_NAME -> result.serviceName
          Phone.NUMBER -> result.serviceNumber
          else -> null
        }
      },
    )

    return cursor
  }

  private fun isCallerAllowed(callingPackage: String?): Boolean {
    if (callingPackage.isNullOrEmpty()) {
      Log.i(TAG, "Calling package is null or empty.")
      return false
    }

    if (callingPackage == requireContext().packageName) {
      return true
    }

    // Check if the calling package is default dialer app or not
    val context = context ?: return false
    val tm = context.getSystemService(TELECOM_SERVICE) as TelecomManager
    return tm.defaultDialerPackage == callingPackage
  }

  private fun getQueryLimit(uri: Uri): Int {
    return try {
      uri.getQueryParameter(ContactsContract.LIMIT_PARAM_KEY)?.toInt() ?: DEFAULT_MAX_RESULTS
    } catch (e: NumberFormatException) {
      DEFAULT_MAX_RESULTS
    }
  }

  companion object {
    private val TAG = SdnProvider::class.java.simpleName

    private const val DIRECTORIES = 0
    private const val FILTER = 1
    private const val CONTACT_LOOKUP = 2
    private const val CONTACT_LOOKUP_WITH_CONTACT_ID = 3
    private const val PHONE_LOOKUP = 4

    private const val ACCOUNT_NAME = "Carrier service numbers"
    private const val ACCOUNT_TYPE = "com.android.contacts.sdn"

    private const val DEFAULT_MAX_RESULTS = 20
  }
}
