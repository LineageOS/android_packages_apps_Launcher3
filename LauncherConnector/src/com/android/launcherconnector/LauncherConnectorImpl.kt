/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcherconnector

import com.android.launcherconnector.proto.DeleteResultRequest
import com.android.launcherconnector.proto.DeleteResultResponse
import com.android.launcherconnector.proto.GetSearchResultsRequest
import com.android.launcherconnector.proto.GetSearchResultsResponse
import com.android.launcherconnector.proto.LauncherConnectorGrpc
import com.android.launcherconnector.proto.NotifyEventRequest
import com.android.launcherconnector.proto.NotifyEventResponse
import com.android.launcherconnector.proto.ReportResultRequest
import com.android.launcherconnector.proto.ReportResultResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver

class LauncherConnectorImpl : LauncherConnectorGrpc.LauncherConnectorImplBase() {

    override fun getSearchResults(
        responseObserver: StreamObserver<GetSearchResultsResponse>
    ): StreamObserver<GetSearchResultsRequest> {
        responseObserver.onError(
            Status.UNIMPLEMENTED.withDescription(unImplementedErrorMessage).asRuntimeException()
        )
        return object : StreamObserver<GetSearchResultsRequest> {
            override fun onNext(value: GetSearchResultsRequest) {}

            override fun onError(t: Throwable) {}

            override fun onCompleted() {}
        }
    }

    override fun deleteResult(
        request: DeleteResultRequest,
        responseObserver: StreamObserver<DeleteResultResponse>,
    ) {
        responseObserver.onError(
            Status.UNIMPLEMENTED.withDescription(unImplementedErrorMessage).asRuntimeException()
        )
    }

    override fun reportResult(
        request: ReportResultRequest,
        responseObserver: StreamObserver<ReportResultResponse>,
    ) {
        responseObserver.onError(
            Status.UNIMPLEMENTED.withDescription(unImplementedErrorMessage).asRuntimeException()
        )
    }

    override fun notifyEvent(
        request: NotifyEventRequest,
        responseObserver: StreamObserver<NotifyEventResponse>,
    ) {
        responseObserver.onError(
            Status.UNIMPLEMENTED.withDescription(unImplementedErrorMessage).asRuntimeException()
        )
    }

    companion object {
        const val unImplementedErrorMessage = "Method is not implemented"
    }
}
