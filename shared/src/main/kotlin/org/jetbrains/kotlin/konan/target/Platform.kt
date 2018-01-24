/*
 * Copyright 2010-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.konan.target

import org.jetbrains.kotlin.konan.properties.*

class Platform(val targetConfigurables: Configurables) 
    : Configurables by targetConfigurables {

    val clang by lazy {
        ClangArgs(targetConfigurables)
    }
    val linker by lazy {
        linker(targetConfigurables)
    }
}

class PlatformManager(properties: Properties, baseDir: String) {
    private val host = TargetManager.host
    private val platforms = TargetManager.enabled.map {
        it to Platform(loadConfigurables(it, properties, baseDir))
    }.toMap()

    fun platform(target: KonanTarget) = platforms[target]!!
    val hostPlatform = platforms[host]!!
}
