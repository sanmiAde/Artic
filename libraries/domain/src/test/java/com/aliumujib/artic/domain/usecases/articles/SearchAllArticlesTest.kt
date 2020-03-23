package com.aliumujib.artic.domain.usecases.articles

import com.aliumujib.artic.domain.exceptions.EmptyQueryException
import com.aliumujib.artic.domain.exceptions.NoParamsException
import com.aliumujib.artic.domain.threadexecutor.PostExecutionThread
import com.aliumujib.artic.domain.models.Article
import com.aliumujib.artic.domain.repositories.articles.IArticlesRepository
import com.aliumujib.artic.domain.testutils.ArticleDataFactory
import com.aliumujib.artic.domain.testutils.TestPostExecutionThreadImpl
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import konveyor.base.randomBuild
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException


class SearchAllArticlesTest {

    private lateinit var searchArticles: SearchAllArticles
    @MockK(relaxed = true)
    lateinit var articlesRepository: IArticlesRepository
    private val postExecutionThread: PostExecutionThread = TestPostExecutionThreadImpl()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        searchArticles = SearchAllArticles(articlesRepository, postExecutionThread)
    }

    @Test
    fun `confirm that calling searchArticles returns data`()= runBlockingTest {
        val list = ArticleDataFactory.makeArticlesList(10)
        stubSearchArticles(flow {
            emit(list)
        })
        val params = SearchAllArticles.Params.make(ArticleDataFactory.makeProject().title, randomBuild())
        val result = searchArticles.build(params).first()
        assertThat(result).isEqualTo(list)
        coVerify(exactly = 1) {
            articlesRepository.searchArticles(any(), any())
        }
    }

    @Test(expected = NoParamsException::class)
    fun `confirm that using searchArticles without params throws an exception`()= runBlockingTest {
        val list = ArticleDataFactory.makeArticlesList(10)
        stubSearchArticles(flow {
            emit(list)
        })
        searchArticles.build().first()
        coVerify(exactly = 0) {
            articlesRepository.searchArticles(any(), any())
        }
    }

    @Test(expected = EmptyQueryException::class)
    fun `confirm that using searchArticles with an emptyString throws an exception`() = runBlockingTest{
        val projects = ArticleDataFactory.makeArticlesList(10)
        stubSearchArticles(flow {
            emit(projects)
        })
        val params = SearchAllArticles.Params.make("", 0)
        searchArticles.build(params).first()
        coVerify(exactly = 0) {
            articlesRepository.searchArticles(any(), any())
        }
    }

    private fun stubSearchArticles(flow: Flow<List<Article>>) {
        every {
            articlesRepository.searchArticles(any(), any())
        } returns flow
    }

}