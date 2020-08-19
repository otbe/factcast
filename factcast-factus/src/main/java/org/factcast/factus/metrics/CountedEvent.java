/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.factus.metrics;

import lombok.Getter;
import lombok.NonNull;

public enum CountedEvent {
    FETCH_SIZE("fetch_size"),
    TRANSACTION_ABORT("transaction_abort"),
    TRANSACTION_RETRY("transaction_retry");

    @NonNull
    @Getter
    final String event;

    CountedEvent(@NonNull String event) {
        this.event = event;
    }
}
