package com.drivest.navigation.trafficsigns

data class TrafficSignsPack(
    val categories: List<TrafficSignCategory>,
    val signs: List<TrafficSign>,
    val sourceReferences: List<String>
)

data class TrafficSignCategory(
    val id: String,
    val name: String,
    val signCount: Int
)

data class TrafficSign(
    val id: String,
    val code: String,
    val caption: String,
    val description: String,
    val officialCategory: String,
    val officialCategories: List<String>,
    val primaryCategoryId: String,
    val categoryIds: List<String>,
    val shape: String,
    val backgroundColor: String,
    val borderColor: String,
    val textHint: String,
    val symbol1: String,
    val symbol2: String,
    val imageAssetPath: String
)

data class TrafficSignsQuizQuestion(
    val sign: TrafficSign,
    val options: List<String>,
    val correctAnswer: String
)
