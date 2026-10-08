@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession

@Composable
fun UserAvatar(
    session: AuthSession?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
    initialTextStyle: TextStyle = MaterialTheme.typography.titleMediumEmphasized,
    fallbackIconSize: Dp = size * 0.55f,
) {
    val context = LocalPlatformContext.current
    val isImageLoaded = remember { mutableStateOf(false) }

    val imageRequest = remember(session?.serverUrl, session?.token) {
        avatarImageSpec(session)?.let { spec ->
            val headers = NetworkHeaders.Builder()
                .set("Authorization", spec.authorization)
                .build()
            ImageRequest.Builder(context)
                .data(spec.url)
                .httpHeaders(headers)
                .crossfade(true)
                .build()
        }
    }

    val initial = remember(session?.user) { avatarInitial(session?.user) }

    if (onClick != null) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(size),
        ) {
            UserAvatarContent(
                modifier = Modifier,
                size = size,
                initial = initial,
                imageRequest = imageRequest,
                imageLoadedState = isImageLoaded,
                initialTextStyle = initialTextStyle,
                fallbackIconSize = fallbackIconSize,
            )
        }
    } else {
        UserAvatarContent(
            modifier = modifier,
            size = size,
            initial = initial,
            imageRequest = imageRequest,
            imageLoadedState = isImageLoaded,
            initialTextStyle = initialTextStyle,
            fallbackIconSize = fallbackIconSize,
        )
    }
}

@Composable
private fun UserAvatarContent(
    modifier: Modifier,
    size: Dp,
    initial: Char?,
    imageRequest: ImageRequest?,
    imageLoadedState: MutableState<Boolean>,
    initialTextStyle: TextStyle,
    fallbackIconSize: Dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageLoadedState.value) {
            if (initial != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial.toString(),
                        style = initialTextStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_user_headshot),
                        contentDescription = null,
                        modifier = Modifier.size(fallbackIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    imageLoadedState.value = state is AsyncImagePainter.State.Success
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
