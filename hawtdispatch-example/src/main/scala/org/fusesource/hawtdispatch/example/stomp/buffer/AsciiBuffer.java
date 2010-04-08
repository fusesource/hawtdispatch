/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.example.stomp.buffer;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class AsciiBuffer extends Buffer {

    private int hashCode;
    private String value;
    
    public AsciiBuffer(Buffer other) {
        super(other);
    }

    public AsciiBuffer(byte[] data, int offset, int length) {
        super(data, offset, length);
    }

    public AsciiBuffer(byte[] data) {
        super(data);
    }

    public AsciiBuffer(String value) {
        super(encode(value));
        this.value = value;
    }

    ///////////////////////////////////////////////////////////////////
    // Overrides
    ///////////////////////////////////////////////////////////////////
    
    public String toString()
    {
    	if( value == null ) {
    		value = decode(this); 
    	}
        return value; 
    }

    @Override
    public boolean equals(Object obj) {
        if( obj==this )
            return true;
         
         if( obj==null || obj.getClass()!=AsciiBuffer.class )
            return false;
         
         return equals((Buffer)obj);
    }
    
    @Override
    public int hashCode() {
        if( hashCode==0 ) {
            hashCode = super.hashCode();;
        }
        return hashCode;
    }
    
    ///////////////////////////////////////////////////////////////////
    // Statics
    ///////////////////////////////////////////////////////////////////

    public static AsciiBuffer ascii(String value) {
        if( value==null ) {
            return null;
        }
        return new AsciiBuffer(value);
    }
    
    public static AsciiBuffer ascii(Buffer buffer) {
        if( buffer==null ) {
            return null;
        }
        if( buffer.getClass() == AsciiBuffer.class ) {
            return (AsciiBuffer) buffer;
        }
        return new AsciiBuffer(buffer);
    }   
    
    static public byte[] encode(String value)
    {
        int size = value.length();
        byte rc[] = new byte[size];
        for( int i=0; i < size; i++ ) {
            rc[i] = (byte)(value.charAt(i)&0xFF);
        }
        return rc;
    }
    
    static public String decode(Buffer value)
    {
        int size = value.getLength();
        char rc[] = new char[size];
        for( int i=0; i < size; i++ ) {
            rc[i] = (char)(value.get(i) & 0xFF );
        }
        return new String(rc);
    }
    
}
