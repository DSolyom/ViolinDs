/*
    Copyright 2016 Dániel Sólyom

    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package ds.violin.v1.app.violin

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import ds.violin.v1.model.entity.HasParcelableData
import ds.violin.v1.model.entity.HasSerializableData
import ds.violin.v1.model.entity.SelfLoadable
import ds.violin.v1.datasource.dataloading.BackgroundDataLoading
import java.io.Serializable
import java.util.*

interface LoadingViolin {

    companion object {
        val STATE_PREFIX = "__loadingviolinregistered_"
        val SAVED_STATES = "__loadingviolinsaved_"
        val IDS_OF_PARCELABLE = "__loadingviolinparcel_"
        val IDS_OF_LOADED = "__loadingviolinidsofloaded_"
    }

    data class RegisteredEntity(val entity: SelfLoadable,
                                val completionOnLoad: (entity: SelfLoadable, Throwable?) -> Unit)

    /** = HashMap(), #Private - holds the [RegisteredEntity]s - @see [registerEntity] */
    val registeredEntities: MutableMap<String, RegisteredEntity>
    /** = ArrayList(), #Private - holds all the currently loading entities @see [loadEntity] */
    val loadingEntities: MutableList<SelfLoadable>
    /** = HashMap(), #Private */
    var savedStates: HashMap<String, Any>
    /** = ArrayList(), #Private */
    var idsOfParcelable: ArrayList<String>
    /** = ArrayList() #ProtectedGet, #PrivateSet */
    var idsOfLoaded: ArrayList<String>

    /**
     * register a [SelfLoadable] entity to automatically handle it's loading and state saving
     *
     * !note: [registerEntity] calls should be the first in [PlayingViolin.play] for full potential
     * !note: registered entities need to implement [HasSerializableData] otherwise their states can't be handled
     * !!note: because play may be called multiple times, make sure not to register more than one
     *         [SelfLoadable] entity with the same id - so create you're [SelfLoadable] entities
     *         beforehand or check if they are already added
     *
     * @param entityId - unique id
     * @param entity
     * @param completion
     */
    fun registerEntity(entityId: String, entity: SelfLoadable,
                       completion: (entity: SelfLoadable, error: Throwable?) -> Unit) {
        if (registeredEntities.containsKey(entityId)) {

            // already has this entityId in
            return
        }
        registeredEntities.put(entityId, RegisteredEntity(entity, completion))

        // restore entity state if we got
        val state = savedStates.remove(entityId)
        if (state != null) {

            if (entity is HasSerializableData) {

                /** had saved [Serializable] state */
                entity.createDataFrom(state as Serializable)
            } else {

                /** had saved [Parcelable] state */
                (entity as HasParcelableData).createDataFrom(state  as Parcelable)
            }

            // only valid entities could've been saved
            entity.valid = true

            // lets mimic load
            if (entity.dataLoader is BackgroundDataLoading) {
                Handler().post {
                    completion(entity, null)
                }
            } else {
                completion(entity, null)
            }
        }
    }

    /**
     * call as one of the last thing you do in [PlayingViolin.play]
     */
    fun play() {
        for (registered in registeredEntities.values) {
            loadEntity(registered.entity, registered.completionOnLoad)
        }
    }

    /**
     * load a [SelfLoadable] entity
     *
     * @param entity
     * @param completion
     * @return true if loading started
     */
    fun loadEntity(entity: SelfLoadable, completion: (entity: SelfLoadable, error: Throwable?) -> Unit): Boolean {
        val loading = entity.load { entity, error ->
            loadingEntities.remove(entity)
            completion(entity, error)
        }
        if (loading) {
            loadingEntities.add(entity)
            return true
        }
        return false
    }

    /**
     * interrupt a [SelfLoadable] entity - use this if [SelfLoadable.load] was called to start the [SelfLoadable]'s loading
     *
     * @param entity
     */
    fun interruptEntity(entity: SelfLoadable) {
        entity.interrupt()
        loadingEntities.remove(entity)
    }

    /**
     * invalidate all registered entities
     *
     * @see [PlayingViolin.invalidateRegisteredEntities]
     */
    fun invalidateRegisteredEntities(subViolinsToo: Boolean = false) {
        for(registered in registeredEntities.values) {
            interruptEntity(registered.entity)
            registered.entity.valid = false
        }
    }

    /**
     * restoring states of registered entities and information about other [SelfLoadable] entities that were loading
     * and had [BackgroundDataLoading.loadId]
     * states will be put in [savedStates] and are restored at [registerEntity]
     * the id's of the [SelfLoadable] entities will be in [idsOfLoaded] but they're need to be handled manually
     * as the completion block is not savable and need to be re set / recreated
     *
     * @param savedInstanceState
     */
    @Suppress("UNCHECKED_CAST")
    fun restoreInstanceState(savedInstanceState: Bundle) {
        /** saved states - only contains [Serializable] at this point */
        savedStates = savedInstanceState.getSerializable(SAVED_STATES) as HashMap<String, Any>
        /** ids of entities that were loading at [saveInstanceState] */
        idsOfLoaded = savedInstanceState.getStringArrayList(IDS_OF_LOADED)

        /** saved parcelable data ids - put parcelable data in saved states too according to these */
        idsOfParcelable = savedInstanceState.getStringArrayList(IDS_OF_PARCELABLE)
        for(id in idsOfParcelable) {
            savedStates.put(id, savedInstanceState.getParcelable(STATE_PREFIX + id))
        }
    }

    /**
     * saving the states of registered entities, and the existence of other entities that are still loading and
     * have a [BackgroundDataLoading.loadId] (via they're [BackgroundDataLoading.loadId])
     *
     * @see [PlayingViolin.saveInstanceState]
     *
     * @param outState
     */
    fun saveInstanceState(outState: Bundle) {

        /** registered entities */
        savedStates.clear()
        idsOfParcelable.clear()

        for (entityId in registeredEntities.keys) {
            val registered = registeredEntities[entityId]!!
            if (registered.entity.valid) {

                /**
                 * it is usually easy to overwrite [Serializable] with [Parcelable],
                 * not that easy the other way around, so start with the latter
                 */
                if (registered.entity is HasParcelableData) {

                    /** [Parcelable] */
                    outState.putParcelable(STATE_PREFIX + entityId, registered.entity.dataToParcelable())
                    idsOfParcelable.add(entityId)
                } else if (registered.entity is HasSerializableData) {

                    /** [Serializable] */
                    val state = registered.entity.dataToSerializable()
                    savedStates.put(entityId, state)
                }
            }
        }

        outState.putSerializable(SAVED_STATES, savedStates)
        outState.putStringArrayList(IDS_OF_PARCELABLE, idsOfParcelable)

        /** loading entities with [BackgroundDataLoading.loadId] */
        idsOfLoaded.clear()

        for (entity in loadingEntities) {
            if (entity.dataLoader is BackgroundDataLoading) {
                val bdl = entity.dataLoader as BackgroundDataLoading
                if (bdl.loadId != null) {
                    idsOfLoaded.add(bdl.loadId!!)
                }
            }
        }

        outState.putStringArrayList(IDS_OF_LOADED, idsOfLoaded)
    }

    /**
     * delay all currently loading entities with [BackgroundDataLoading] - call from
     * [Activity.onPause] and [Fragment.onPause]
     * this makes the loadingEntities to receive completion calls only if the Violin is active
     *
     * !note: unless onDestroy is called first, in which case only entities with loaders with
     * [BackgroundDataLoading.loadId] will do
     */
    fun onPause() {
        for (entity in loadingEntities) {
            if (entity.dataLoader is BackgroundDataLoading) {
                val bdl = entity.dataLoader as BackgroundDataLoading
                bdl.delayForThis()
            }
        }
    }

    /**
     * need to stop/delay all currently loading entities with [BackgroundDataLoading] - call from
     * [Activity.onDestroy] and [Fragment.onDestroy]
     */
    fun onDestroy() {
        for (i in (loadingEntities.size - 1) downTo 0) {
            val entity = loadingEntities[i]
            if (entity.dataLoader is BackgroundDataLoading) {
                val bdl = entity.dataLoader as BackgroundDataLoading
                when (bdl.loadId) {
                    null -> interruptEntity(entity)
                    else -> bdl.delayForNextLoader()
                }
            } else {
                interruptEntity(entity)
            }
        }
    }

    /**
     * stop all [loadingEntities]
     */
    fun stopEverything() {
        for (i in (loadingEntities.size - 1) downTo 0) {
            interruptEntity(loadingEntities[i])
        }
    }
}
