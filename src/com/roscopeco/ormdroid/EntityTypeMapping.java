/*
 * Copyright 2012 Ross Bamford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package com.roscopeco.ormdroid;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.roscopeco.ormdroid.Entity.EntityMapping;

import lombok.extern.slf4j.Slf4j;

/**
 * Standard mapping for {@link Entity} subclasses. Automatically saves the
 * model if it's transient during encoding, when called from a 
 * {@link Entity#save()}. When called for query creation, will throw
 * an exception if the object is transient. Encodes value as an 
 * INTEGER with the value being the model's primary key.
 */
@Slf4j
public class EntityTypeMapping implements TypeMapping {
  private static final String TAG = "ORM: ETM";
  
  public Class<?> javaType() {
    return Entity.class;
  }

  public String sqlType(Class<?> concreteType) {    
    @SuppressWarnings("unchecked")
    EntityMapping map = Entity.getEntityMapping((Class<? extends Entity>)concreteType);
    
    // TODO ON_UPDATE, ON_DELETE etc.
    
    return "INTEGER REFERENCES " + map.mTableName + "(" + map.mPrimaryKeyColumnName + ")";
  }
  
  public String encodeValue(Object value) {
    Entity model = (Entity)value;
    
    if (model.isTransient()) {
      return TypeMapper.encodeValue(model.save());
    } else {    
      return TypeMapper.encodeValue(model.getPrimaryKeyValue());
    }
  }

  // TODO Fix this, it's a mess:
  //
  //            * It's hardcoded for int primary keys
  //            * It's inefficient
  //            * It's generally a mess...
  public Object decodeValue(Class<?> expectedType, Cursor c, int columnIndex) {
    if (Entity.class.isAssignableFrom(expectedType)) {
      @SuppressWarnings("unchecked")
      Class<? extends Entity> expEntityType = (Class<? extends Entity>)expectedType;

      // TODO could use Query here? Maybe Query could have a primaryKey() method to select by prikey?
      EntityMapping map = Entity.getEntityMappingEnsureSchema(expEntityType);
      String sql = "SELECT * FROM " + map.mTableName + " WHERE " + map.mPrimaryKeyColumnName + "=" + c.getInt(columnIndex) + " LIMIT 1";
      log.trace(sql);
      Cursor valc = ORMDroidApplication.getInstance().query(sql);
      if (valc.moveToFirst()) {
        return map.load(valc);
      } else {
        return null;
      }      
    } else {
      throw new IllegalArgumentException("EntityTypeMapping can only be used with Entity subclasses");
    }
  }
}
