/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

@SuppressWarnings("unchecked")
public class CopyOnWriteCaseInsensitiveHashMapTestCase extends CaseInsensitiveHashMapTestCase
{

    @SuppressWarnings("rawtypes")
    @Override
    protected Map createTestMap()
    {
        return new CopyOnWriteCaseInsensitiveHashMap<String, String>(super.createTestMap());
    }

    @Test
    public void copyOnPut() throws Exception
    {
        Map<String, Object> original = super.createTestMap();
        Map<String, Object> copyOnWriteMap = new CopyOnWriteCaseInsensitiveHashMap<String, Object>(original);

        copyOnWriteMap.put("other", "val");

        // Assert state of original map
        doTestMap(original);
        assertEquals(2, original.size());
        assertFalse(original.containsKey("other"));

        // Assert state of copy on write map
        doTestMap(copyOnWriteMap);
        assertEquals(3, copyOnWriteMap.size());
        assertTrue(copyOnWriteMap.containsKey("other"));
    }

    @Test
    public void copyOnPutAll() throws Exception
    {
        Map<String, Object> original = super.createTestMap();
        Map<String, Object> copyOnWriteMap = new CopyOnWriteCaseInsensitiveHashMap<String, Object>(original);

        Map<String, String> extrasMap = new HashMap<String, String>();
        extrasMap.put("extra1", "val");
        extrasMap.put("extra2", "val");
        extrasMap.put("extra3", "val");
        copyOnWriteMap.putAll(extrasMap);

        // Assert state of original map
        doTestMap(original);
        assertEquals(2, original.size());
        assertFalse(original.containsKey("extra1"));
        assertFalse(original.containsKey("extra2"));
        assertFalse(original.containsKey("extra3"));

        // Assert state of copy on write map
        doTestMap(copyOnWriteMap);
        assertEquals(5, copyOnWriteMap.size());
        assertTrue(copyOnWriteMap.containsKey("extra1"));
        assertTrue(copyOnWriteMap.containsKey("extra2"));
        assertTrue(copyOnWriteMap.containsKey("extra3"));
    }

    @Test
    public void copyOnPutRemove() throws Exception
    {
        Map<String, Object> original = super.createTestMap();
        original.put("extra", "value");
        Map<String, Object> copyOnWriteMap = new CopyOnWriteCaseInsensitiveHashMap<String, Object>(original);

        copyOnWriteMap.remove("extra");

        // Assert state of original map
        doTestMap(original);
        assertEquals(3, original.size());
        assertTrue(original.containsKey("extra"));

        // Assert state of copy on write map
        doTestMap(copyOnWriteMap);
        assertEquals(2, copyOnWriteMap.size());
        assertFalse(copyOnWriteMap.containsKey("extra"));
    }

    @Test
    public void copyOnClear() throws Exception
    {
        Map<String, Object> original = super.createTestMap();
        Map<String, Object> copyOnWriteMap = new CopyOnWriteCaseInsensitiveHashMap<String, Object>(original);

        copyOnWriteMap.clear();

        // Assert state of original map
        doTestMap(original);
        assertEquals(2, original.size());

        // Assert state of copy on write map
        assertEquals(0, copyOnWriteMap.size());
    }

}
