/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.common.compat;

import android.test.AndroidTestCase;

public class CompatUtilsTest extends AndroidTestCase {

    public void testIsClassAvailable_NullClassName() {
        assertFalse(CompatUtils.isClassAvailable(null));
    }

    public void testIsClassAvailable_EmptyClassName() {
        assertFalse(CompatUtils.isClassAvailable(""));
    }

    public void testIsClassAvailable_NonexistentClass() {
        assertFalse(CompatUtils.isClassAvailable("com.android.contacts.common.NonexistentClass"));
    }

    public void testIsClassAvailable() {
        assertTrue(CompatUtils.isClassAvailable(BaseClass.class.getName()));
    }

    public void testIsMethodAvailable_NullClassName() {
        assertFalse(CompatUtils.isMethodAvailable(null, "methodName"));
    }

    public void testIsMethodAvailable_EmptyClassName() {
        assertFalse(CompatUtils.isMethodAvailable("", "methodName"));
    }

    public void testIsMethodAvailable_NullMethodName() {
        assertFalse(CompatUtils.isMethodAvailable("className", null));
    }

    public void testIsMethodAvailable_EmptyMethodName() {
        assertFalse(CompatUtils.isMethodAvailable("className", ""));
    }

    public void testIsMethodAvailable_NonexistentClass() {
        assertFalse(CompatUtils.isMethodAvailable("com.android.contacts.common.NonexistentClass",
                ""));
    }

    public void testIsMethodAvailable_NonexistentMethod() {
        assertFalse(CompatUtils.isMethodAvailable(BaseClass.class.getName(), "derivedMethod"));
    }

    public void testIsMethodAvailable() {
        assertTrue(CompatUtils.isMethodAvailable(BaseClass.class.getName(), "baseMethod"));
    }

    public void testIsMethodAvailable_InheritedMethod() {
        assertTrue(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "baseMethod"));
    }

    public void testIsMethodAvailable_OverloadedMethod() {
        assertTrue(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "overloadedMethod"));
        assertTrue(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "overloadedMethod",
                Integer.TYPE));
    }

    public void testIsMethodAvailable_NonexistentOverload() {
        assertFalse(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "overloadedMethod",
                Boolean.TYPE));
    }

    private class BaseClass {
        public void baseMethod() {}
    }

    private class DerivedClass extends BaseClass {
        public void derivedMethod() {}

        public void overloadedMethod() {}

        public void overloadedMethod(int i) {}
    }
}
