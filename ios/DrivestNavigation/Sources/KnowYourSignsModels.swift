import Foundation

struct KnowYourSignsTheoryPack: Decodable {
    let meta: KnowYourSignsMeta
    let chapters: [KnowYourSignsChapter]

    static func empty() -> KnowYourSignsTheoryPack {
        KnowYourSignsTheoryPack(
            meta: KnowYourSignsMeta(
                dataset: "",
                version: "",
                createdUTC: "",
                note: ""
            ),
            chapters: []
        )
    }
}

struct KnowYourSignsMeta: Decodable {
    let dataset: String
    let version: String
    let createdUTC: String
    let note: String

    enum CodingKeys: String, CodingKey {
        case dataset
        case version
        case createdUTC = "created_utc"
        case note
    }
}

struct KnowYourSignsChapter: Decodable {
    let chapterId: String
    let title: String
    let overview: String
    let sections: [KnowYourSignsSection]

    enum CodingKeys: String, CodingKey {
        case chapterId = "chapter_id"
        case title
        case overview
        case sections
    }
}

struct KnowYourSignsSection: Decodable {
    let sectionId: String
    let title: String
    let summary: String
    let keyPoints: [String]
    let commonExamFocus: [String]
    let quickChecks: [String]
    let signs: [KnowYourSign]

    enum CodingKeys: String, CodingKey {
        case sectionId = "section_id"
        case title
        case summary
        case keyPoints = "key_points"
        case commonExamFocus = "common_exam_focus"
        case quickChecks = "quick_checks"
        case signs
    }
}

struct KnowYourSign: Decodable {
    let signId: String
    let code: String
    let title: String
    let imagePath: String
    let meaning: String
    let driverAction: String
    let memoryHint: String
    let category: String

    enum CodingKeys: String, CodingKey {
        case signId = "sign_id"
        case code
        case title
        case imagePath = "image_path"
        case meaning
        case driverAction = "driver_action"
        case memoryHint = "memory_hint"
        case category
    }
}

struct KnowYourSignsQuestion: Decodable {
    let id: Int
    let topic: String
    let difficulty: String
    let question: String
    let imagePath: String?
    let options: [String]
    let correctAnswerIndex: Int
    let explanation: String
    let signId: String?

    enum CodingKeys: String, CodingKey {
        case id
        case topic
        case difficulty
        case question
        case imagePath = "image_path"
        case options
        case correctAnswerIndex = "correct_answer_index"
        case explanation
        case signId = "sign_id"
    }
}
