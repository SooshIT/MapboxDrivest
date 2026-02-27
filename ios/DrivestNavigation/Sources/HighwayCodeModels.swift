import Foundation

struct HighwayCodeTheoryPack: Decodable {
    let meta: HighwayCodeMeta
    let chapters: [HighwayCodeChapter]

    static func empty() -> HighwayCodeTheoryPack {
        HighwayCodeTheoryPack(
            meta: HighwayCodeMeta(
                dataset: "",
                version: "",
                createdUTC: "",
                scopeNote: "",
                sourceRoot: ""
            ),
            chapters: []
        )
    }
}

struct HighwayCodeMeta: Decodable {
    let dataset: String
    let version: String
    let createdUTC: String
    let scopeNote: String
    let sourceRoot: String

    enum CodingKeys: String, CodingKey {
        case dataset
        case version
        case createdUTC = "created_utc"
        case scopeNote = "scope_note"
        case sourceRoot = "source_root"
    }
}

struct HighwayCodeChapter: Decodable {
    let chapterId: String
    let title: String
    let sourceURL: String
    let overview: String
    let sections: [HighwayCodeSection]

    enum CodingKeys: String, CodingKey {
        case chapterId = "chapter_id"
        case title
        case sourceURL = "source_url"
        case overview
        case sections
    }
}

struct HighwayCodeSection: Decodable {
    let sectionId: String
    let title: String
    let summary: String
    let keyRules: [String]
    let commonMistakes: [String]
    let quickQuizPrompts: [String]

    enum CodingKeys: String, CodingKey {
        case sectionId = "section_id"
        case title
        case summary
        case keyRules = "key_rules"
        case commonMistakes = "common_mistakes"
        case quickQuizPrompts = "quick_quiz_prompts"
    }
}

struct HighwayCodeQuestion: Decodable {
    let id: Int
    let topic: String
    let difficulty: String
    let question: String
    let options: [String]
    let correctAnswerIndex: Int
    let explanation: String

    enum CodingKeys: String, CodingKey {
        case id
        case topic
        case difficulty
        case question
        case options
        case correctAnswerIndex = "correct_answer_index"
        case explanation
    }
}
