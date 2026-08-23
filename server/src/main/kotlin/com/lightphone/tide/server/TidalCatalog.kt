package com.lightphone.tide.server

import android.net.Uri
import com.lightphone.tide.protocol.Page
import com.lightphone.tide.protocol.ReleaseSummary
import com.lightphone.tide.protocol.ReleaseType
import com.tidal.sdk.auth.CredentialsProvider
import com.tidal.sdk.tidalapi.generated.TidalApiClient
import com.tidal.sdk.tidalapi.generated.models.AlbumsAttributes
import com.tidal.sdk.tidalapi.generated.models.AlbumsResourceObject
import com.tidal.sdk.tidalapi.generated.models.ArtistsResourceObject
import com.tidal.sdk.tidalapi.generated.models.IncludedInner
import com.tidal.sdk.tidalapi.generated.models.UserCollectionAlbumsItemsMultiRelationshipDataDocument

/**
 * Server-side catalog repository. Fetches saved releases from the TIDAL v2 API
 * and maps the JSON:API response to protocol-owned [ReleaseSummary] models.
 */
class TidalCatalog(
    credentialsProvider: CredentialsProvider,
) {
    private val client = TidalApiClient(credentialsProvider = credentialsProvider)
    private val api = client.createUserCollectionAlbums()

    suspend fun collection(cursor: String?): Page<ReleaseSummary> {
        val response = api.userCollectionAlbumsIdRelationshipsItemsGet(
            id = "me",
            pageCursor = cursor,
            sort = listOf(SortByDateAddedDescending),
            include = listOf("items", "items.artists"),
        )

        if (!response.isSuccessful) {
            throw TidalCatalogException("TIDAL API returned HTTP ${response.code()}: ${response.message()}")
        }

        val body = response.body()
            ?: throw TidalCatalogException("TIDAL API returned empty response body")

        return mapResponse(body)
    }

    private fun mapResponse(body: UserCollectionAlbumsItemsMultiRelationshipDataDocument): Page<ReleaseSummary> {
        val included = body.included.orEmpty()
        val albumsById = included.filterIsInstance<AlbumsResourceObject>().associateBy { it.id }
        val artistsById = included.filterIsInstance<ArtistsResourceObject>().associateBy { it.id }

        val items = body.data.orEmpty().mapNotNull { identifier ->
            val album = albumsById[identifier.id] ?: return@mapNotNull null
            mapAlbum(album, artistsById)
        }

        val nextCursor = body.links.next?.let(::extractCursor)
        return Page(items = items, nextCursor = nextCursor)
    }

    private fun mapAlbum(
        album: AlbumsResourceObject,
        artistsById: Map<String, ArtistsResourceObject>,
    ): ReleaseSummary {
        val attrs = album.attributes
        val artistIds = album.relationships?.artists?.data.orEmpty().map { it.id }
        val artistName = artistIds
            .mapNotNull { artistsById[it]?.attributes?.name }
            .firstOrNull() ?: "Unknown Artist"

        return ReleaseSummary(
            id = album.id,
            title = attrs?.title ?: "Untitled",
            artistName = artistName,
            type = mapReleaseType(attrs?.albumType),
            itemCount = attrs?.numberOfItems ?: 0,
        )
    }

    private fun mapReleaseType(type: AlbumsAttributes.AlbumType?): ReleaseType = when (type) {
        AlbumsAttributes.AlbumType.ALBUM -> ReleaseType.Album
        AlbumsAttributes.AlbumType.EP -> ReleaseType.Ep
        AlbumsAttributes.AlbumType.SINGLE -> ReleaseType.Single
        null -> ReleaseType.Album
    }

    private fun extractCursor(nextUrl: String): String? =
        Uri.parse(nextUrl).getQueryParameter("page[cursor]")

    companion object {
        private const val SortByDateAddedDescending = "-addedAt"
    }
}

class TidalCatalogException(message: String) : Exception(message)
