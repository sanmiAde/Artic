/*
 * Copyright 2020 Abdul-Mujeeb Aliu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliumujib.artic.remote.impl

import com.aliumujib.artic.data.model.ArticleEntity
import com.aliumujib.artic.remote.api.WordPressAPI
import com.aliumujib.artic.remote.mapper.PostsMapper
import com.aliumujib.artic.data.repositories.articles.remote.IArticlesRemote
import javax.inject.Inject

class ArticlesRemote @Inject constructor(
    var wordPressAPI: WordPressAPI,
    var postsMapper: PostsMapper
) : IArticlesRemote {

    override suspend fun getArticles(page: Int, count: Int): List<ArticleEntity> {
        return postsMapper.mapModelList(wordPressAPI.getPostByPage(page, count).posts)
    }

    override suspend fun getArticle(id: Int): ArticleEntity {
        return postsMapper.mapFromModel(wordPressAPI.getPostDetialsById(id).post)
    }

    override suspend fun searchArticles(search: String, page: Int, count: Int): List<ArticleEntity> {
        return postsMapper.mapModelList(wordPressAPI.getSearchPosts(search, count).posts)
    }

    override suspend fun getArticlesByCategoryId(categoryId: Int, page: Int, count: Int): List<ArticleEntity> {
        return postsMapper.mapModelList(wordPressAPI.getCategoryDetailsByPage(categoryId, page, count).posts)
    }

    companion object {
        private const val PER_PAGE = 5
    }

}
