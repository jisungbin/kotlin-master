/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

/**
 * This extension integrates with call resolution mechanism:
 *
 * - resolution and completion of the receiver
 * - resolution of arguments
 * - resolution of the call itself
 *   - [intercept] is called
 * - resolution of the outer call
 * - completion of the call
 *   - [transform] is called
 * - completion of the outer call
 *
 * !!!! This extension is highly unstable and not recommended to use !!!!
 *
 *
 *
 * 이 확장은 호출 해석 메커니즘과 통합됩니다.
 *
 * - 수신자 해석 및 완료
 * - 인자 해석
 * - 호출 자체의 해석
 *   - [intercept]가 호출됩니다.
 * - 외부 호출의 해석
 * - 호출의 완료
 *   - [transform]이 호출됩니다.
 * - 외부 호출의 완료
 *
 * ⚠️ 이 확장은 매우 불안정하며, 사용을 권장하지 않습니다.
 */
@FirExtensionApiInternals
abstract class FirFunctionCallRefinementExtension(session: FirSession) : FirExtension(session) {
  companion object {
    val NAME: FirExtensionPointName = FirExtensionPointName("FunctionCallRefinementExtension")
  }

  final override val name: FirExtensionPointName
    get() = NAME

  final override val extensionType: KClass<out FirExtension> = FirFunctionCallRefinementExtension::class

  /**
   * Allows a call to be completed with a more specific type than the declared return type of function.
   *
   * ```
   * interface Container<out T> { }
   * fun Container<T>.add(item: String): Container<Any>
   * ```
   *
   * at call site `Container<Any>` can be modified to become `Container<NewLocalType>`.
   *
   * ```
   * container.add("A")
   * ```
   *
   * this `NewLocalType` can be created in [intercept]. It must be later saved into FIR tree in
   * generated declarations should be local because this [FirExtension] works at body resolve stage
   * and hus cannot create new top level declarations.
   *
   * When [intercept] returns non-null value, a copy will be created from FirFunction that [symbol]
   * points to. Copy will be used in call completion instead of original function.
   *
   * @return null if plugin is not interested in a [symbol].
   *
   *
   *
   * 함수의 선언된 반환 타입보다 더 구체적인 타입으로 호출을 완료할 수 있게 합니다.
   *
   * ```
   * interface Container<out T> { }
   * fun Container<T>.add(item: String): Container<Any>
   * ```
   *
   * 호출 시점에서 `Container<Any>`는 `Container<NewLocalType>`으로 수정될 수 있습니다.
   *
   * ```
   * container.add("A")
   * ```
   *
   * 이때 NewLocalType은 [intercept]에서 생성될 수 있습니다. 이후 FIR 트리에 저장되어야 합니다.
   * 생성된 선언은 지역적으로만 존재해야 하는데, 이는 [FirExtension]이 body resolve 단계에서 동작하며
   * 새로운 최상위 선언을 만들 수 없기 때문입니다.
   *
   * [intercept]가 null이 아닌 값을 반환하면, [symbol]이 가리키는 FirFunction의 복사본이 생성되며
   * 이 복사본이 원래 함수 대신 호출 완료에 사용됩니다.
   *
   * @return 플러그인이 [symbol]에 관심이 없으면 null을 반환합니다.
   */
  abstract fun intercept(callInfo: CallInfo, symbol: FirNamedFunctionSymbol): CallReturnType?

  /**
   * Data can be associated with [FirNamedFunctionSymbol] in [callback].
   *
   * 데이터는 [callback]에서 [FirNamedFunctionSymbol]에 연결할 수 있습니다.
   */
  class CallReturnType(val typeRef: FirResolvedTypeRef, val callback: ((FirNamedFunctionSymbol) -> Unit)? = null)

  /**
   * @param call to a function that was created with modified [FirResolvedTypeRef] as a result of [intercept].
   *  This function doesn't exist in FIR, it is needed to complete the call.
   *
   * @param originalSymbol [intercept] is called with symbol to a declaration that exists somewhere in FIR:
   *  library, project code. * The same symbol is [originalSymbol]. [transform] needs to generate call to
   *  [let] with the same return type as [call] and put all generated declarations used in [FirResolvedTypeRef]
   *  in statements.
   *
   *
   *
   * @param call [intercept]의 결과로 수정된 [FirResolvedTypeRef]를 사용해 생성된 함수 호출입니다.
   *  이 함수는 FIR 내에 존재하지 않으며, 호출을 완료하기 위해 필요합니다.
   *
   * @param originalSymbol [intercept]는 FIR 어딘가에 존재하는 선언의 심볼과 함께 호출됩니다. 이 심볼이
   * 바로 [originalSymbol]입니다. [transform]은 [call]과 동일한 반환 타입을 가지는 [let] 호출을 생성하고,
   * [FirResolvedTypeRef]에서 사용된 모든 생성된 선언을 statement로 추가해야 합니다.
   */
  abstract fun transform(call: FirFunctionCall, originalSymbol: FirNamedFunctionSymbol): FirFunctionCall

  /**
   * Needs to return true for local classes generated in [transform].
   *
   * [transform]에서 생성된 로컬 클래스에는 true를 반환해야 합니다.
   */
  abstract fun ownsSymbol(symbol: FirRegularClassSymbol): Boolean

  /**
   * [transform] creates new local classes following this pattern:
   *
   * `call()` => `run { *declarations*; call() as Container<NewType> }`
   *
   * This function needs to return a source element of `call()` before transformation - *original call*,
   * as typed by users.
   *
   *
   *
   * [transform]은 다음 패턴으로 로컬 클래스를 생성합니다.
   *
   * `call() => run { *declarations*; call() as Container<NewType> }`
   *
   * 이 함수는 변환 이전의 call() 원본 호출(사용자가 작성한 형태)의 소스 요소를 반환합니다.
   */
  abstract fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement

  /**
   * [call] - FIR of *original call* returned by [anchorElement].
   *
   * Needs to find a symbol associated with transformed call with given name.
   * This function assumes that generated local classes have unique names.
   *
   *
   *
   * [call]: [anchorElement]가 반환한 원본 호출의 FIR입니다.
   *
   * 주어진 이름을 가진 변환된 호출과 연결된 심볼을 찾아야 합니다.
   * 이 함수는 생성된 로컬 클래스의 이름이 고유하다고 가정합니다.
   */
  abstract fun restoreSymbol(call: FirFunctionCall, name: Name): FirRegularClassSymbol?

  fun interface Factory : FirExtension.Factory<FirFunctionCallRefinementExtension>
}

@OptIn(FirExtensionApiInternals::class)
val FirExtensionService.callRefinementExtensions: List<FirFunctionCallRefinementExtension> by FirExtensionService.registeredExtensions()

@OptIn(FirExtensionApiInternals::class)
internal class OriginalCallData(val originalSymbol: FirNamedFunctionSymbol, val extension: FirFunctionCallRefinementExtension)

internal object OriginalCallDataKey : FirDeclarationDataKey()

internal var FirDeclaration.originalCallDataForPluginRefinedCall: OriginalCallData? by FirDeclarationDataRegistry.data(OriginalCallDataKey)
