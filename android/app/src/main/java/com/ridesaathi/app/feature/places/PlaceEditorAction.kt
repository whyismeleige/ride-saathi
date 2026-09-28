package com.ridesaathi.app.feature.places

import com.ridesaathi.app.domain.model.PlaceCandidate

internal sealed interface PlaceEditorAction {
    data class Rename(val name: String) : PlaceEditorAction
    data class Query(val query: String) : PlaceEditorAction
    data class SelectAddress(val candidate: PlaceCandidate) : PlaceEditorAction
    data object Search : PlaceEditorAction
    data object ChangeAddress : PlaceEditorAction
    data object Save : PlaceEditorAction
    data object AskDelete : PlaceEditorAction
    data object DismissDelete : PlaceEditorAction
    data object Delete : PlaceEditorAction
}
