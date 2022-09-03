package eu.kanade.domain.source.model

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata

typealias SourcePagingSourceType = PagingSource<Long, /*SY --> */ Pair<SManga, RaisedSearchMetadata?>/*SY <-- */>
