/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.fluss.flink.lakehouse.paimon.reader;

import org.apache.paimon.data.InternalRow;
import org.apache.paimon.utils.ProjectedRow;

/** An {@link InternalRow} with the key part. */
public class KeyValueRow {

    private final boolean isDelete;
    private final ProjectedRow pkRow;
    private final InternalRow valueRow;

    public KeyValueRow(int[] indexes, InternalRow valueRow, boolean isDelete) {
        this.pkRow = ProjectedRow.from(indexes).replaceRow(valueRow);
        this.valueRow = valueRow;
        this.isDelete = isDelete;
    }

    public boolean isDelete() {
        return isDelete;
    }

    public InternalRow keyRow() {
        return pkRow;
    }

    public InternalRow valueRow() {
        return valueRow;
    }
}
