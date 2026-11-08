package one.tunkshif.swirlmusic.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.BuildConfig
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.UserProfile
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

/**
 * Profile: identity block on top, then a grouped list of account rows. Sign-out sits behind a
 * confirmation dialog.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var confirmSignOut by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = contentPadding.calculateTopPadding() + Spacing.xl,
                    start = contentPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + Spacing.xl,
                    end = contentPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + Spacing.xl,
                    bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.displaySmall,
            )

            IdentityBlock(profile)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    ProfileRow(
                        iconRes = R.drawable.ic_info,
                        title = stringResource(R.string.profile_version),
                        supporting = BuildConfig.VERSION_NAME,
                    )
                    ProfileRow(
                        iconRes = R.drawable.ic_logout,
                        title = stringResource(R.string.profile_sign_out),
                        supporting = null,
                        accent = true,
                        onClick = { confirmSignOut = true },
                    )
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.profile_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.profile_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) {
                    Text(stringResource(R.string.profile_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IdentityBlock(profile: UserProfile?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (profile?.avatarUrl != null) {
            Artwork(
                url = profile.avatarUrl,
                contentDescription = profile.nickname,
                shape = MaterialShapes.Cookie9Sided.toShape(),
                modifier = Modifier.size(128.dp),
            )
        } else {
            Surface(
                modifier = Modifier.size(128.dp),
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile?.initials ?: "?",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Text(
            text = profile?.nickname ?: stringResource(R.string.profile_unknown_user),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        if (profile?.isVip == true) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.profile_vip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }

        Text(
            text = stringResource(
                if (profile == null) R.string.profile_load_failed else R.string.profile_signed_in,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileRow(
    @DrawableRes iconRes: Int,
    title: String,
    supporting: String?,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val titleColor = if (accent) MaterialTheme.colorScheme.error else Color.Unspecified
    val iconContainer = if (accent) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = if (accent) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = iconTint,
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (accent) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        ProfileScreen(
            profile = UserProfile(1, "tunkshif", null, vipType = 0),
            onSignOut = {},
        )
    }
}
