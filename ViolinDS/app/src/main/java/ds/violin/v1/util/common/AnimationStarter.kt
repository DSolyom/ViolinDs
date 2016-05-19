/*
	Copyright 2013 Dániel Sólyom

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/
package ds.violin.v1.util.common

import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.view.View
import android.widget.ImageView

object AnimationStarter {

    fun start(view: ImageView) {
        val aDrawable = view.drawable as? AnimationDrawable

        if (aDrawable != null) {

            // stupid stupid way - but no animation otherwise
            val handler = Handler()

            object : Thread() {
                override fun run() {
                    handler.post {
                        if (view.visibility == View.VISIBLE) {
                            aDrawable.start()
                        }
                    }
                }
            }.start()
        }
    }

    fun start(animation: AnimationDrawable) {

        // stupid stupid way - but no animation otherwise
        val handler = Handler()

        object : Thread() {
            override fun run() {
                handler.post { animation.start() }
            }
        }.start()
    }
}
