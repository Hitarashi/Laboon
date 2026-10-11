package org.shilpo.laboon.home

import org.json.JSONArray
import org.json.JSONObject
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.fold

/** Owns authenticated playlist transport and JSON decoding. */
internal class UserPlaylistClient(
    private val sessionStore: SessionStore,
    private val http: HttpJsonClient = HttpJsonClient(),
    private val importHttp: HttpJsonClient = HttpJsonClient(readTimeoutMs = IMPORT_TIMEOUT_MS),
) {
    suspend fun list(): Result<List<SavedPlaylist>> = request("", method = RequestMethod.GET) { body ->
        UserPlaylistApi.parsePlaylists(JSONArray(body))
    }

    suspend fun get(id: String): Result<SavedPlaylistDetail> =
        request("/${id.pathSegment()}", RequestMethod.GET) { body ->
            UserPlaylistApi.parseDetail(JSONObject(body)) ?: error("Malformed playlist response")
        }

    suspend fun create(name: String): Result<SavedPlaylist> =
        request("", RequestMethod.POST, UserPlaylistApi.createBody(name)) { body ->
            UserPlaylistApi.parsePlaylist(JSONObject(body)) ?: error("Malformed playlist response")
        }

    suspend fun import(
        name: String,
        sourceUrl: String,
        trackIds: List<String>,
    ): Result<SavedPlaylist> = request(
        "/import",
        RequestMethod.POST,
        UserPlaylistApi.importBody(name, sourceUrl, trackIds),
        requestClient = importHttp,
    ) { body ->
        UserPlaylistApi.parsePlaylist(JSONObject(body)) ?: error("Malformed playlist response")
    }

    suspend fun rename(id: String, name: String): Result<SavedPlaylist> =
        request("/${id.pathSegment()}", RequestMethod.PATCH, UserPlaylistApi.renameBody(name)) { body ->
            UserPlaylistApi.parsePlaylist(JSONObject(body)) ?: error("Malformed playlist response")
        }

    suspend fun replaceTracks(
        id: String,
        trackIds: List<String>,
    ): Result<SavedPlaylistDetail> = request(
        "/${id.pathSegment()}/tracks",
        RequestMethod.PUT,
        UserPlaylistApi.replaceTracksBody(trackIds),
    ) { body ->
        UserPlaylistApi.parseDetail(JSONObject(body)) ?: error("Malformed playlist response")
    }

    suspend fun delete(id: String): Result<Unit> = request(
        "/${id.pathSegment()}",
        RequestMethod.DELETE,
    ) { _ -> }

    private suspend fun <T> request(
        path: String,
        method: RequestMethod,
        body: String? = null,
        requestClient: HttpJsonClient = http,
        parse: (String) -> T,
    ): Result<T> {
        val session = sessionStore.getSession() ?: return Result.failure(IllegalStateException("Sign in first"))
        val url = session.serverUrl.trimEnd('/') + "/api/v1/playlists$path"
        val headers = mapOf("Authorization" to "Bearer ${session.token}")
        val outcome = when (method) {
            RequestMethod.GET -> requestClient.get(url, headers)
            RequestMethod.POST -> requestClient.postJson(url, body.orEmpty(), headers)
            RequestMethod.PATCH -> requestClient.patchJson(url, body.orEmpty(), headers)
            RequestMethod.PUT -> requestClient.putJson(url, body.orEmpty(), headers)
            RequestMethod.DELETE -> requestClient.delete(url, headers)
        }
        return outcome.fold(
            onSuccess = { response -> runCatching { parse(response) } },
            onFailure = { error -> Result.failure(IllegalStateException(error.message, error.cause)) },
        )
    }

    private fun String.pathSegment(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private enum class RequestMethod { GET, POST, PATCH, PUT, DELETE }

    private companion object {
        const val IMPORT_TIMEOUT_MS = 30_000L
    }
}
