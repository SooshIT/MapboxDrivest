import Foundation

struct TheoryPack: Codable {
    let version: String
    let updatedAt: String
    let topics: [TheoryTopic]
    let lessons: [TheoryLesson]
    let signs: [TheorySign]
    let questions: [TheoryQuestion]

    static func empty() -> TheoryPack {
        TheoryPack(version: "empty", updatedAt: "", topics: [], lessons: [], signs: [], questions: [])
    }
}

struct TheoryTopic: Codable {
    let id: String
    let title: String
    let description: String
    let tags: [String]
    let lessonIds: [String]
    let questionIds: [String]
}

struct TheoryLesson: Codable {
    let id: String
    let topicId: String
    let title: String
    let content: String
    let keyPoints: [String]
    let signIds: [String]
}

struct TheorySign: Codable {
    let id: String
    let topicId: String
    let name: String
    let meaning: String
    let memoryHint: String
}

struct TheoryQuestion: Codable {
    let id: String
    let topicId: String
    let prompt: String
    let options: [TheoryAnswerOption]
    let correctOptionId: String
    let explanation: String
    let difficulty: String
}

struct TheoryAnswerOption: Codable {
    let id: String
    let text: String
}

enum TheoryQuizMode: String {
    case topic
    case bookmarks
    case wrong
    case mock
}
