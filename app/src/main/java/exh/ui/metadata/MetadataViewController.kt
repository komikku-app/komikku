package exh.ui.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.databinding.MetadataViewControllerBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MetadataViewController : NucleusController<MetadataViewControllerBinding, MetadataViewPresenter> {
    constructor(manga: Manga) : super(
        bundleOf(
            MangaController.MANGA_EXTRA to manga.id,
        ),
    ) {
        this.manga = manga
        source = Injekt.get<SourceManager>().getOrStub(manga.source)
    }

    constructor(mangaId: Long) : this(
        runBlocking { Injekt.get<GetManga>().await(mangaId)!! },
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(MangaController.MANGA_EXTRA))

    var adapter: MetadataViewAdapter? = null

    var manga: Manga? = null
        private set
    var source: Source? = null
        private set

    override fun getTitle(): String? {
        return manga?.title
    }

    override fun createPresenter(): MetadataViewPresenter {
        return MetadataViewPresenter(
            manga!!,
            source!!,
        )
    }

    override fun createBinding(inflater: LayoutInflater) = MetadataViewControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        if (manga == null || source == null) return
        binding.recycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        adapter = MetadataViewAdapter()
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
    }

    fun onNextMangaInfo(meta: RaisedSearchMetadata?) {
        val adapter = adapter ?: return
        val context = view?.context ?: return
        adapter.items = meta?.getExtraInfoPairs(context).orEmpty()
    }
}
