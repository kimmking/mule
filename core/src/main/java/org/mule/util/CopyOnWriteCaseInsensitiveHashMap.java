/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class CopyOnWriteCaseInsensitiveHashMap<K, V> implements Map<K, V>, Serializable
{

    private static final long serialVersionUID = 4829196240419943077L;

    private final Map<K, V> original;

    private final ReentrantLock copyLock = new ReentrantLock();
    private transient AtomicBoolean copied = new AtomicBoolean(false);
    private transient Map<K, V> copy;

    public CopyOnWriteCaseInsensitiveHashMap(Map<K, V> original)
    {
        this.original = Collections.unmodifiableMap(original);
    }

    @Override
    public int size()
    {
        return getDelegate().size();
    }

    @Override
    public boolean isEmpty()
    {
        return getDelegate().isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return getDelegate().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        return getDelegate().containsValue(value);
    }

    @Override
    public V get(Object key)
    {
        return getDelegate().get(key);
    }

    @Override
    public V put(K key, V value)
    {
        copy();
        return copy.put(key, value);
    }

    @Override
    public V remove(Object key)
    {
        copy();
        return (V) copy.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> t)
    {
        copy();
        copy.putAll(t);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void clear()
    {
        copy();
        copy = new CaseInsensitiveHashMap();
    }

    @Override
    public Set<K> keySet()
    {
        return getDelegate().keySet();
    }

    @Override
    public Collection<V> values()
    {
        return getDelegate().values();
    }

    @Override
    public Set<Entry<K, V>> entrySet()
    {
        return getDelegate().entrySet();
    }

    @Override
    public String toString()
    {
        return getDelegate().toString();
    }

    private Map<K, V> getDelegate()
    {
        if (copied.get())
        {
            return copy;
        }
        else
        {
            return original;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void copy()
    {
        if (!copied.get())
        {
            try
            {
                copyLock.lock();
                if (copied.compareAndSet(false, true))
                {
                    copy = new CaseInsensitiveHashMap(original);
                }
            }
            finally
            {
                copyLock.unlock();
            }
        }
    }

    /**
     * After deserialization we can just use unserialized original map directly.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        copy = original;
        copied = new AtomicBoolean(true);
    }
}
