/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.scopes.FirContainingNamesAwareScope
import org.jetbrains.kotlin.fir.scopes.impl.FirClassDeclaredMemberScope
import org.jetbrains.kotlin.fir.scopes.impl.FirNestedClassifierScope
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

/*
 * TODO:
 *  - check that annotations or meta-annotations is not empty
 */

/**
 * All `generate*` members have the contract that the computation should be side-effect-free.
 *
 * That means that all `generate*` function implementations should not modify any state or leak
 * the generated `FirElement` or `FirBasedSymbol` (e.g., by putting it to some cache).
 *
 * This restriction is imposed by the corresponding IDE cache implementation, which might retry
 * the computation several times.
 *
 *
 *
 * generate* 멤버들은 모두 부작용이 없어야 한다는 계약을 가집니다.
 *
 * 즉, 모든 generate* 함수 구현은 어떤 상태도 수정하거나, 생성된 `FirElement`나 `FirBasedSymbol`을
 * 외부로 노출(예: 캐시에 저장)해서는 안 됩니다.
 *
 * 이 제한은 IDE의 캐시 구현 방식 때문에 존재하며, IDE가 동일한 계산을 여러 번 재시도할 수 있기
 * 때문입니다.
 */
abstract class FirDeclarationGenerationExtension(session: FirSession) : FirExtension(session) {
  companion object {
    val NAME: FirExtensionPointName = FirExtensionPointName("ExistingClassModification")
  }

  final override val name: FirExtensionPointName
    get() = NAME

  final override val extensionType: KClass<out FirExtension> = FirDeclarationGenerationExtension::class

  /**
   * Can be called on SUPERTYPES stage.
   *
   * If classId has `outerClassId.Companion` format then generated class should be a companion object.
   *
   *
   * SUPERTYPES 단계에서 호출할 수 있습니다.
   *
   * classId가 `outerClassId.Companion` 형식이라면, 생성된 클래스는 `companion object`여야 합니다.
   */
  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? = null

  open fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? = null

  // Can be called on STATUS stage.
  open fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> = emptyList()
  open fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> = emptyList()
  open fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> = emptyList()

  // Can be called on IMPORTS stage.
  open fun hasPackage(packageFqName: FqName): Boolean = false

  /**
   * Can be called on SUPERTYPES stage.
   *
   * `generate...` methods will be called only if `get...Names/ClassIds/CallableIds` returned corresponding
   * declaration name.
   *
   * If you want to generate constructor for some class, then you need to return `SpecialNames.INIT` in
   * set of callable names for this class.
   *
   *
   *
   * SUPERTYPES 단계에서 호출할 수 있습니다.
   *
   * `get...Names/ClassIds/CallableIds`가 해당 선언 이름을 반환한 경우에만 `generate...` 메서드들이 호출됩니다.
   *
   * 특정 클래스의 생성자를 생성하려면, 해당 클래스의 callable 이름 집합에 `SpecialNames.INIT`를 반환해야 합니다.
   */
  open fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> = emptySet()
  open fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext): Set<Name> = emptySet()

  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun getTopLevelCallableIds(): Set<CallableId> = emptySet()

  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun getTopLevelClassIds(): Set<ClassId> = emptySet()

  fun interface Factory : FirExtension.Factory<FirDeclarationGenerationExtension>

  // ----------------------------------- internal utils -----------------------------------

  @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
  @FirExtensionApiInternals
  val topLevelClassIdsCache: FirLazyValue<Set<ClassId>> =
    session.firCachesFactory.createLazyValue { getTopLevelClassIds() }

  @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
  @FirExtensionApiInternals
  val topLevelCallableIdsCache: FirLazyValue<Set<CallableId>> =
    session.firCachesFactory.createLazyValue { getTopLevelCallableIds() }
}

typealias MemberGenerationContext = DeclarationGenerationContext.Member
typealias NestedClassGenerationContext = DeclarationGenerationContext.Nested

sealed class DeclarationGenerationContext<T : FirContainingNamesAwareScope>(
  val owner: FirClassSymbol<*>,
  val declaredScope: T?,
) {
  // is needed for `hashCode` implementation
  protected abstract val kind: Int

  class Member(
    owner: FirClassSymbol<*>,
    declaredScope: FirClassDeclaredMemberScope?,
  ) : DeclarationGenerationContext<FirClassDeclaredMemberScope>(owner, declaredScope) {
    override val kind: Int
      get() = 1
  }

  class Nested(
    owner: FirClassSymbol<*>,
    declaredScope: FirNestedClassifierScope?,
  ) : DeclarationGenerationContext<FirNestedClassifierScope>(owner, declaredScope) {
    override val kind: Int
      get() = 2
  }

  override fun equals(other: Any?): Boolean {
    if (this.javaClass !== other?.javaClass) {
      return false
    }
    require(other is DeclarationGenerationContext<*>)
    return owner == other.owner
  }

  override fun hashCode(): Int {
    return owner.hashCode() + kind
  }
}

val FirExtensionService.declarationGenerators: List<FirDeclarationGenerationExtension> by FirExtensionService.registeredExtensions()
