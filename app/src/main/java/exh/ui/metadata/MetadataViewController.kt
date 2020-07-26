package exh.ui.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.MetadataViewControllerBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.LewdSource.Companion.getLewdSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MetadataViewController : NucleusController<MetadataViewControllerBinding, MetadataViewPresenter> {
    constructor(manga: Manga?) : super(
        Bundle().apply {
            putLong(MangaController.MANGA_EXTRA, manga?.id ?: 0)
        }
    ) {
        this.manga = manga
        if (manga != null) {
            source = Injekt.get<SourceManager>().getOrStub(manga.source)
        }
    }

    constructor(mangaId: Long) : this(
        Injekt.get<DatabaseHelper>().getManga(mangaId).executeAsBlocking()
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(MangaController.MANGA_EXTRA))

    var data = emptyList<Pair<String, String>>()

    var adapter: MetadataViewAdapter? = null

    var manga: Manga? = null
        private set
    var source: Source? = null
        private set

    override fun getTitle(): String? {
        return manga?.title
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = MetadataViewControllerBinding.inflate(inflater)
        return binding.root
    }

    override fun createPresenter(): MetadataViewPresenter {
        return MetadataViewPresenter(
            manga!!,
            source!!
        )
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        if (manga == null || source == null) return
        binding.recycler.layoutManager = GridLayoutManager(view.context, 2)
        adapter = MetadataViewAdapter(data)
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
    }

    fun onNextMetaInfo(flatMetadata: FlatMetadata) {
        val thisSourceAsLewdSource = presenter.source.getLewdSource()
        if (thisSourceAsLewdSource != null) {
            presenter.meta = flatMetadata.raise(thisSourceAsLewdSource.metaClass)
        }
    }

    fun onNextMangaInfo(meta: RaisedSearchMetadata?) {
        val context = view?.context ?: return
        data = meta?.getExtraInfoPairs(context) ?: emptyList()
        adapter?.update(data)
    }
}
