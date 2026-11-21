package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConfirmDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConflictDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoCreateDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoDeleteDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionStoresScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeExtensionReposScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { AnimeExtensionReposScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is AnimeRepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeRepoScreenState.Success

        // Visual e funções iguais à "Loja de extensões" do mangá
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val disabledRepos by sourcePreferences.disabledRepos().collectAsState()
        val stores = remember(successState.repos) {
            successState.repos.map { repo ->
                ExtensionStore(
                    indexUrl = repo.baseUrl,
                    name = repo.name,
                    badgeLabel = repo.shortName.orEmpty(),
                    signingKey = repo.signingKeyFingerprint,
                    contact = ExtensionStore.Contact(website = repo.website, discord = null),
                    isLegacy = false,
                    extensionListUrl = null,
                )
            }
        }

        fun setDisabled(indexUrl: String, disabled: Boolean) {
            val current = sourcePreferences.disabledRepos().get()
            sourcePreferences.disabledRepos().set(if (disabled) current + indexUrl else current - indexUrl)
            screenModel.refreshExtensionList()
            context.toast(KMR.strings.extensions_page_need_refresh)
        }

        ExtensionStoresScreen(
            state = ExtensionStoreScreenState.Success(stores = stores, disabledRepos = disabledRepos),
            onClickCreate = { screenModel.showDialog(AnimeRepoDialog.Create) },
            onCopy = { context.copyToClipboard(it.indexUrl, it.indexUrl) },
            onOpenWebsite = { context.openInBrowser(it.contact.website) },
            onOpenDiscord = { },
            onClickEnable = { setDisabled(it.indexUrl, disabled = false) },
            onClickDisable = { setDisabled(it.indexUrl, disabled = true) },
            onClickDelete = { store -> screenModel.showDialog(AnimeRepoDialog.Delete(store.indexUrl)) },
            onClickRefresh = { screenModel.refreshRepos() },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeRepoDialog.Create -> {
                ExtensionRepoCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(it) },
                    repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                )
            }
            is AnimeRepoDialog.Delete -> {
                ExtensionRepoDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteRepo(dialog.repo) },
                    repo = dialog.repo,
                )
            }
            is AnimeRepoDialog.Conflict -> {
                ExtensionRepoConflictDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onMigrate = { screenModel.replaceRepo(dialog.newRepo) },
                    oldRepo = dialog.oldRepo,
                    newRepo = dialog.newRepo,
                )
            }
            is AnimeRepoDialog.Confirm -> {
                ExtensionRepoConfirmDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(dialog.url) },
                    repo = dialog.url,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is AnimeRepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
