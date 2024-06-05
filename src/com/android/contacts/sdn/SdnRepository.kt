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

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.android.contacts.model.SimCard
import com.android.contacts.util.PermissionsUtil
import com.android.contacts.util.PhoneNumberHelper

/** Repository to fetch Sdn data from [CarrierConfigManager]. */
class SdnRepository constructor(private val context: Context) {

  fun isSdnPresent(): Boolean {
    if (
      !hasTelephony() ||
      !PermissionsUtil.hasPermission(context, permission.READ_PHONE_STATE) ||
      !PermissionsUtil.hasPermission(context, permission.READ_PHONE_NUMBERS) ||
      !PermissionsUtil.hasPermission(context, permission.READ_CALL_LOG)
    ) {
      return false
    }

    val simCardList = getSimCardInformation()

    for (simCard in simCardList) {
      if (fetchSdnFromCarrierConfig(simCard).isNotEmpty()) {
        Log.i(TAG, "Found SDN list from CarrierConfig")
        return true
      }
    }
    return false
  }

  fun fetchSdn(): List<Sdn> {
    val simCardList = getSimCardInformation()

    return simCardList
      .flatMap { fetchSdnFromCarrierConfig(it) }
      .distinct()
      .sortedBy { it.serviceName }
  }

  // Permission check isn't recognized by the linter.
  @SuppressLint("MissingPermission")
  fun getSimCardInformation(): List<SimCard> {
    val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
    return subscriptionManager?.activeSubscriptionInfoList?.filterNotNull()?.mapNotNull {
      if (it.subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        null
      } else {
        SimCard.create(it)
      }
    }
      ?: emptyList()
  }

  @Suppress("Deprecation", "MissingPermission")
  private fun fetchSdnFromCarrierConfig(simCard: SimCard): List<Sdn> {
    val carrierConfigManager = context.getSystemService(CarrierConfigManager::class.java)
    val carrierConfig =
      carrierConfigManager?.getConfigForSubId(simCard.subscriptionId) ?: return emptyList()
    val nameList: List<String> =
      carrierConfig
        .getStringArray(CarrierConfigManager.KEY_CARRIER_SERVICE_NAME_STRING_ARRAY)
        ?.map { it?.trim() ?: "" }
        ?: return emptyList()
    val numberList: List<String> =
      carrierConfig
        .getStringArray(CarrierConfigManager.KEY_CARRIER_SERVICE_NUMBER_STRING_ARRAY)
        ?.map { it?.trim() ?: "" }
        ?: return emptyList()
    if (nameList.isEmpty() || nameList.size != numberList.size) return emptyList()

    val sdnList = mutableListOf<Sdn>()
    nameList.zip(numberList).forEach { (sdnServiceName, sdnNumber) ->
      if (sdnServiceName.isNotBlank() && PhoneNumberHelper.isDialablePhoneNumber(sdnNumber)) {
        sdnList.add(Sdn(sdnServiceName, sdnNumber))
      }
    }
    return sdnList
  }

  private fun hasTelephony(): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
  }

  companion object {
    private val TAG = SdnRepository::class.java.simpleName
  }
}

/** Hold the Service dialing number information to be displayed in SdnActivity. */
data class Sdn(
  val serviceName: String,
  val serviceNumber: String,
) {

  /** Generate lookup key that will help identify SDN when Opening QuickContact. */
  fun lookupKey(): String {
    return "non-sim-sdn-" + hashCode()
  }
}
