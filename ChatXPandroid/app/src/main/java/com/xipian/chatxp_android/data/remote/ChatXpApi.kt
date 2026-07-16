package com.xipian.chatxp_android.data.remote

import com.xipian.chatxp_android.data.remote.dto.AnonymousAuthRequestDto
import com.xipian.chatxp_android.data.remote.dto.DataEnvelopeDto
import com.xipian.chatxp_android.data.remote.dto.GenerationDto
import com.xipian.chatxp_android.data.remote.dto.MessageListDto
import com.xipian.chatxp_android.data.remote.dto.ModelListDto
import com.xipian.chatxp_android.data.remote.dto.RefreshRequestDto
import com.xipian.chatxp_android.data.remote.dto.SessionListDto
import com.xipian.chatxp_android.data.remote.dto.SessionDto
import com.xipian.chatxp_android.data.remote.dto.SessionUpdateRequestDto
import com.xipian.chatxp_android.data.remote.dto.TokenDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("api/v1/auth/anonymous")
    suspend fun anonymous(
        @Body body: AnonymousAuthRequestDto
    ): DataEnvelopeDto<TokenDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): DataEnvelopeDto<TokenDto>
}

interface ChatXpApi {
    @GET("api/v1/models")
    suspend fun models(): DataEnvelopeDto<ModelListDto>

    @GET("api/v1/sessions")
    suspend fun sessions(
        @Query("q") query: String? = null,
        @Query("limit") limit: Int = 30
    ): DataEnvelopeDto<SessionListDto>

    @PATCH("api/v1/sessions/{sessionId}")
    suspend fun updateSession(
        @Path("sessionId") sessionId: String,
        @Body body: SessionUpdateRequestDto
    ): DataEnvelopeDto<SessionDto>

    @GET("api/v1/sessions/{sessionId}/messages")
    suspend fun messages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int = 50
    ): DataEnvelopeDto<MessageListDto>

    @GET("api/v1/generations/by-client-request/{clientRequestId}")
    suspend fun generation(
        @Path("clientRequestId") clientRequestId: String
    ): DataEnvelopeDto<GenerationDto>
}
