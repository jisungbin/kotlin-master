/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

/**
 * This OptIn is used to mark direct access of .declarations property of FirFile/FirClass/FirScript,
 * see KT-75498.
 *
 * Direct access to .declarations can be risky for various reasons:
 *
 * - one doesn't see any plugin-generated declarations
 * - in IDE mode, there are no guarantees about a reached resolve phase
 *
 * It's recommended, especially in checkers, to use scope-based methods, like processAllDeclarations,
 * processAllDeclaredCallables, declaredProperties, declaredFunctions, constructors, etc. The typical
 * way looks like this:
 *
 * ```
 * someClass(Symbol).processAllDeclarations(session) { it: FirBasedSymbol<*> ->
 *     // it is a declaration symbol
 * }
 * ```
 *
 *
 * 이 OptIn은 FirFile, FirClass, FirScript의 .declarations 프로퍼티에 직접 접근하는 경우를 표시하기 위해
 * 사용됩니다. (참고: KT-75498)
 *
 * .declarations에 직접 접근하는 것은 여러 이유로 위험할 수 있습니다:
 *
 * - 플러그인이 생성한 선언을 확인할 수 없습니다.
 * - IDE 모드에서는 접근 시점의 resolve 단계가 보장되지 않습니다.
 *
 * 특히 **checker**에서는 processAllDeclarations, processAllDeclaredCallables, declaredProperties,
 * declaredFunctions, constructors 등과 같은 스코프 기반 메서드를 사용하는 것이 권장됩니다.
 *
 * 일반적인 사용 예시는 다음과 같습니다:
 *
 * ```
 * someClass(Symbol).processAllDeclarations(session) { it: FirBasedSymbol<*> ->
 *     // it은 선언 심볼입니다.
 * }
 * ```
 */
@RequiresOptIn(
  message = "Please use FirClass(Symbol).processAllDeclarations or similar functions instead, see also " +
    "KDoc of DirectDeclarationsAccess",
)
annotation class DirectDeclarationsAccess
