/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.providers

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.getProperties
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@RequiresOptIn
annotation class FirSymbolProviderInternals

/**
 * A symbol provider provides [class symbols][FirClassLikeSymbol] and [callable symbols][FirCallableSymbol]
 * from a specific source, such as source files, libraries, or generated symbols.
 *
 * [FirSymbolProvider] is an abstract class instead of an interface by design: symbol providers are queried
 * frequently by the compiler and are often used in hot spots. The `vtable` dispatch for abstract classes is
 * generally faster than `itable` dispatch for interfaces. While that difference might be optimized away during
 * [JVM dispatch optimizations](https://shipilev.net/blog/2015/black-magic-method-dispatch/), the abstract
 * class guarantees that we can fall back to the faster `vtable` dispatch at more complicated call sites.
 *
 *
 * 심벌 프로바이더(Symbol Provider)는 소스 파일, 라이브러리, 혹은 생성된 심벌과 같은 특정 소스에서
 * [클래스 심벌][FirClassLikeSymbol]과 [호출 가능 심벌][FirCallableSymbol]을 제공합니다.
 *
 * [FirSymbolProvider]는 인터페이스가 아닌 추상 클래스로 설계되었습니다. 이는 심벌 프로바이더가 컴파일러에
 * 의해 매우 자주 조회되며, 성능이 중요한 지점(hot spot)에서 사용되기 때문입니다. 추상 클래스의 vtable
 * 디스패치는 일반적으로 인터페이스의 `itable` 디스패치보다 빠르기 때문입니다.
 *
 * JVM 디스패치 최적화 과정에서 이 차이가 상쇄될 수도 있지만, 추상 클래스 구조를 사용하면 복잡한 호출
 * 지점에서도 보다 빠른 vtable 디스패치로 안전하게 폴백할 수 있음이 보장됩니다.
 */
abstract class FirSymbolProvider(val session: FirSession) : FirSessionComponent {
  abstract val symbolNamesProvider: FirSymbolNamesProvider

  /**
   * Returns a [FirClassLikeSymbol] with the given [classId], or `null` if such a symbol cannot be found.
   *
   * In case of multiple declarations sharing the same class ID, [getClassLikeSymbolByClassId] consistently
   * returns a symbol for one of these declarations. However, which declaration is initially chosen is not
   * defined, meaning the symbol provider may choose any one of the possible declarations at its leisure.
   *
   *
   * 지정된 [classId]에 해당하는 [FirClassLikeSymbol]을 반환합니다. 만약 해당 심벌을 찾을 수 없다면 null을
   * 반환합니다.
   *
   * 동일한 클래스 ID를 공유하는 여러 선언이 존재하는 경우, [getClassLikeSymbolByClassId]는 그중 하나의
   * 선언에 대한 심벌을 일관되게 반환합니다. 단, 어떤 선언이 처음에 선택되는지는 명확히 정의되어 있지 않으며,
   * 심벌 프로바이더는 가능한 선언들 중 임의로 하나를 선택할 수 있습니다.
   */
  abstract fun getClassLikeSymbolByClassId(classId: ClassId): FirClassLikeSymbol<*>?

  @OptIn(FirSymbolProviderInternals::class)
  open fun getTopLevelCallableSymbols(packageFqName: FqName, name: Name): List<FirCallableSymbol<*>> {
    return buildList { getTopLevelCallableSymbolsTo(this, packageFqName, name) }
  }

  @FirSymbolProviderInternals
  abstract fun getTopLevelCallableSymbolsTo(destination: MutableList<FirCallableSymbol<*>>, packageFqName: FqName, name: Name)

  @OptIn(FirSymbolProviderInternals::class)
  open fun getTopLevelFunctionSymbols(packageFqName: FqName, name: Name): List<FirNamedFunctionSymbol> {
    return buildList { getTopLevelFunctionSymbolsTo(this, packageFqName, name) }
  }

  @FirSymbolProviderInternals
  abstract fun getTopLevelFunctionSymbolsTo(destination: MutableList<FirNamedFunctionSymbol>, packageFqName: FqName, name: Name)

  @OptIn(FirSymbolProviderInternals::class)
  open fun getTopLevelPropertySymbols(packageFqName: FqName, name: Name): List<FirPropertySymbol> {
    return buildList { getTopLevelPropertySymbolsTo(this, packageFqName, name) }
  }

  @FirSymbolProviderInternals
  abstract fun getTopLevelPropertySymbolsTo(destination: MutableList<FirPropertySymbol>, packageFqName: FqName, name: Name)

  abstract fun hasPackage(fqName: FqName): Boolean
}

private fun FirSession.getClassDeclaredMemberScope(classId: ClassId): FirScope? {
  val classSymbol = symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol ?: return null
  return declaredMemberScope(classSymbol.fir, memberRequiredPhase = null)
}

fun FirSession.getClassDeclaredPropertySymbols(classId: ClassId, name: Name): List<FirVariableSymbol<*>> {
  val classMemberScope = getClassDeclaredMemberScope(classId)
  return classMemberScope?.getProperties(name).orEmpty()
}

fun FirSession.getRegularClassSymbolByClassIdFromDependencies(classId: ClassId): FirRegularClassSymbol? {
  return dependenciesSymbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol
}

fun FirSession.getRegularClassSymbolByClassId(classId: ClassId): FirRegularClassSymbol? {
  return symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol
}

val FirSession.symbolProvider: FirSymbolProvider by FirSession.sessionComponentAccessor()

const val DEPENDENCIES_SYMBOL_PROVIDER_QUALIFIED_KEY: String = "org.jetbrains.kotlin.fir.resolve.providers.FirDependenciesSymbolProvider"

val FirSession.dependenciesSymbolProvider: FirSymbolProvider by FirSession.sessionComponentAccessor(
  DEPENDENCIES_SYMBOL_PROVIDER_QUALIFIED_KEY,
)
