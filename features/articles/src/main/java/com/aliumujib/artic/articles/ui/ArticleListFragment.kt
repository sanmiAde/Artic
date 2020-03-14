package com.aliumujib.artic.articles.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.aliumujib.artic.articles.databinding.ArticleListFragmentBinding
import com.aliumujib.artic.articles.di.ArticleListModule
import com.aliumujib.artic.articles.di.DaggerArticleListComponent
import com.aliumujib.artic.views.models.ArticleUIModel
import com.aliumujib.artic.articles.models.ArticleUIModelMapper
import com.aliumujib.artic.articles.presentation.ArticleListIntent
import com.aliumujib.artic.articles.presentation.ArticleListViewModel
import com.aliumujib.artic.articles.presentation.ArticleListViewState
import com.aliumujib.artic.articles.ui.adapter.ArticleClickListener
import com.aliumujib.artic.articles.ui.adapter.ArticleListAdapter
import com.aliumujib.artic.mobile_ui.ApplicationClass.Companion.coreComponent
import com.aliumujib.artic.views.ext.*
import com.aliumujib.artic.views.mvi.MVIView
import com.aliumujib.artic.views.recyclerview.GridSpacingItemDecoration
import com.eyowo.android.core.utils.autoDispose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import reactivecircus.flowbinding.recyclerview.scrollStateChanges
import timber.log.Timber
import javax.inject.Inject


@ExperimentalCoroutinesApi
class ArticleListFragment : Fragment(), MVIView<ArticleListIntent, ArticleListViewState>,
    ArticleClickListener {

    @Inject
    lateinit var viewModel: ArticleListViewModel

    @Inject
    lateinit var articlesAdapter: ArticleListAdapter

    @Inject
    lateinit var articleUIModelMapper: ArticleUIModelMapper

    private var _binding by autoDispose<ArticleListFragmentBinding>()
    private val binding get() = _binding

    private val _loadInitialIntent = BroadcastChannel<ArticleListIntent>(1)
    private val loadInitialIntent = _loadInitialIntent.asFlow().take(1)
    //Is this really the best [BroadcastChannel] to use here? TODO replace with https://github.com/Kotlin/kotlinx.coroutines/pull/1354 as soon as its out

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = ArticleListFragmentBinding.inflate(inflater, container, false)
        return _binding.root
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        initDependencyInjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.processActions()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        lifecycleScope.launch {
            _loadInitialIntent.consumeEach {
                Timber.d("New intent from activity: $it")
            }
        }

        viewModel.processIntent(intents())
        _loadInitialIntent.offer(ArticleListIntent.LoadArticleListIntent(true))

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews()

        observeStates()
    }

    private fun observeStates() {
        viewModel.states()
            .onEach {
                render(it)
            }.launchIn(lifecycleScope)
    }

    private fun initializeViews() {
        val staggeredGridLayoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
        binding.articles.apply {
            removeAllDecorations()
            addItemDecoration(
                GridSpacingItemDecoration(
                    2,
                    context.dpToPx(16),
                    true
                )
            )
            layoutManager = staggeredGridLayoutManager
            adapter = articlesAdapter
        }
    }

    private fun loadMoreIntent(): Flow<ArticleListIntent> {
        return binding.articles.scrollStateChanges()
            .filter { _ -> !articlesAdapter.isLoadingNextPage() } //only runs when adapter is NOT loading
            .filter { event -> event == RecyclerView.SCROLL_STATE_IDLE }
            .filter { _ -> binding.articles.isLastItemDisplaying() }
            .map {
                ArticleListIntent.FetchMoreArticleListIntent
            }
    }

    private fun loadInitialIntent(): Flow<ArticleListIntent> {
        return loadInitialIntent.filter { articlesAdapter.isEmpty() } //only runs when adapter is empty
    }


    override fun render(state: ArticleListViewState) {
        when {
            !state.isLoading && (state.error == null) -> presentSuccessState(articleUIModelMapper.mapToUIList(state.data))
            state.error != null -> presentErrorState(state.error, state.isLoadingMore)
            state.isLoading -> presentLoadingState(state.isGrid, state.isLoadingMore)
        }
    }

    private fun presentSuccessState(data: List<ArticleUIModel>) {
        binding.shimmerViewContainer.stopShimmerAnimation()
        articlesAdapter.setListState(ArticleListAdapter.ListState.Idle)
        binding.gridLoading.hide()
        binding.listLoading.hide()

        if (data.isNotEmpty()) {
            binding.emptyView.hide()
            binding.errorView.hide()
            binding.articles.show()
        } else {
            binding.emptyView.show()
            binding.errorView.hide()
            binding.articles.hide()
        }

        articlesAdapter.submitList(data)
    }

    private fun presentErrorState(error: Throwable, isLoadingMoreData: Boolean) {
        binding.emptyView.hide()
        binding.shimmerViewContainer.stopShimmerAnimation()
        binding.shimmerViewContainer.hide()
        if (isLoadingMoreData) {
            binding.articles.show()
            articlesAdapter.setListState(ArticleListAdapter.ListState.Error(error))
        } else {
            binding.articles.hide()
            binding.errorView.show()
        }
        error.message?.let {
            binding.errorView.setErrorViewText(it)
        }
        Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
    }

    private fun presentLoadingState(isGrid: Boolean, isLoadingMoreData: Boolean) {
        binding.shimmerViewContainer.show()
        binding.shimmerViewContainer.startShimmerAnimation()
        when {
            isLoadingMoreData -> {
                binding.articles.show()
                articlesAdapter.setListState(ArticleListAdapter.ListState.Loading)
                return
            }
            isGrid -> {
                binding.articles.hide()
                binding.listLoading.hide()
                binding.gridLoading.show()
            }
            else -> {
                binding.articles.hide()
                binding.listLoading.show()
                binding.gridLoading.hide()
            }
        }
        binding.emptyView.hide()
        binding.errorView.hide()
    }

    /**
     * Initialize dagger injection dependency graph.
     */
    private fun initDependencyInjection() {
        DaggerArticleListComponent
            .builder()
            .coreComponent(coreComponent(requireContext()))
            .articleListModule(ArticleListModule(this))
            .build()
            .inject(this)
    }

    override fun intents(): Flow<ArticleListIntent> {
        return merge(loadMoreIntent(), loadInitialIntent())
            .onEach {
                delay(500)
            }
            .conflate()
    }

    override fun invoke(articleUIModel: ArticleUIModel) {
        findNavController().navigate(ArticleListFragmentDirections.actionArticleListFragmentToNavDetails(articleUIModel))
    }


}
