/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed -> in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.konan.target

class ClangManager(hostConfigurables: Configurables, val targetConfigurables: Configurables) {
    val hostArgs = ClangHostArgs(hostConfigurables)
    val targetArgs = ClangTargetArgs(targetConfigurables)

    val targetClangArgs = 
        (hostArgs.commonClangArgs + targetArgs.specificClangArgs).toTypedArray()

    val targetClangArgsForKonanSources =
        targetClangArgs + targetArgs.clangArgsSpecificForKonanSources

    val targetLibclangArgs: List<String> get() {
        // libclang works not exactly the same way as the clang binary and
        // (in particular) uses different default header search path.
        // See e.g. http://lists.llvm.org/pipermail/cfe-dev/2013-November/033680.html
        // We workaround the problem with -isystem flag below.
        val llvmVersion = targetConfigurables.llvmVersion
        val llvmHome = targetConfigurables.absoluteLlvmHome
        val isystemArgs = listOf("-isystem", "$llvmHome/lib/clang/$llvmVersion/include")

        return isystemArgs + targetClangArgs.toList()
    }

    val hostCompilerArgsForJni = hostArgs.hostCompilerArgsForJni.toTypedArray()

    val targetClangCmd
        = listOf("${targetConfigurables.absoluteLlvmHome}/bin/clang") + targetClangArgs

    val targetClangXXCmd
        = listOf("${targetConfigurables.absoluteLlvmHome}/bin/clang++") + targetClangArgs

    fun clangC(vararg userArgs: String) = targetClangCmd + userArgs.asList()

    fun clangCXX(vararg userArgs: String) = targetClangXXCmd + userArgs.asList()
}
