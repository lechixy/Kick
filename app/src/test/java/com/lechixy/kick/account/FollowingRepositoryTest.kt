package com.lechixy.kick.account

import com.lechixy.kick.data.account.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class FollowingRepositoryTest {
    @Test fun followMutationUsesServerResultAndFailureDoesNotChangeList() = runBlocking<Unit> {
        var followed = true
        var failMutation = false
        val methods = mutableListOf<String>()
        val auth = signedInAuth { request ->
            if (request.url.encodedPath.endsWith("/follow")) {
                methods.add(request.method)
                if (failMutation) throw AccountApiException(403)
                followed = request.method == "POST"
                objectJson("{}")
            } else if (followed) objectJson("""{"channels":[{"channel_slug":"one","is_live":true}]}""")
            else objectJson("""{"channels":[]}""")
        }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.loaded } }
            repo.setFollowing("one", false)
            withTimeout(3000) { repo.state.first { it.loaded && !it.loading && it.channels.isEmpty() } }
            failMutation = true
            assertTrue(runCatching { repo.setFollowing("one", true) }.isFailure)
            assertTrue(repo.state.value.channels.isEmpty())
            assertEquals(listOf("DELETE", "POST"), methods)
        } finally { job.cancelAndJoin() }
    }

    @Test fun cursorPaginationDeduplicatesChannelsAndStopsRepeatedCursor() = runBlocking<Unit> {
        val auth = signedInAuth { request ->
            if (request.url.queryParameter("cursor") == null) objectJson("""{
                "nextCursor":5,"channels":[{"channel_slug":"one","user_username":"One","is_live":true}]}""")
            else objectJson("""{"nextCursor":5,"channels":[
                {"channel_slug":"one","is_live":true},{"channel_slug":"two","is_live":false}]}""")
        }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.loaded } }
            assertEquals("5", repo.state.value.nextCursor)
            repo.loadMore()
            withTimeout(3000) { repo.state.first { !it.loading && it.channels.size == 2 } }
            assertNull(repo.state.value.nextCursor)
            assertTrue(repo.state.value.channels[0].live)
            assertFalse(repo.state.value.channels[1].live)
            auth.signOut()
            withTimeout(3000) { repo.state.first { it.channels.isEmpty() && !it.loaded } }
        } finally { job.cancelAndJoin() }
    }
    @Test fun endpointErrorIsNotAnEmptyFollowingListAndCanRetry() = runBlocking<Unit> {
        var fail = true
        val auth = signedInAuth { if (fail) throw AccountApiException(403) else objectJson("""{"channels":[],"nextCursor":null}""") }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.error != null } }
            assertFalse(repo.state.value.loaded)
            assertTrue(auth.session.value.signedIn)
            fail = false; repo.refresh()
            withTimeout(3000) { repo.state.first { it.loaded } }
            assertTrue(repo.state.value.channels.isEmpty())
            assertNull(repo.state.value.error)
        } finally { job.cancelAndJoin() }
    }
}
