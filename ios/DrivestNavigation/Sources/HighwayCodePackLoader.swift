import Foundation

@MainActor
final class HighwayCodePackLoader {
    static let shared = HighwayCodePackLoader()

    private let lock = NSLock()
    private var cachedTheory: HighwayCodeTheoryPack?
    private var cachedQuestions: [HighwayCodeQuestion]?

    func loadTheory() -> HighwayCodeTheoryPack {
        lock.lock()
        if let cachedTheory {
            lock.unlock()
            return cachedTheory
        }
        lock.unlock()

        guard
            let url = Bundle.main.url(
                forResource: "Drivest_HighwayCode_Theory_Expanded_v2",
                withExtension: "json",
                subdirectory: "Data/highwaycode"
            ),
            let data = try? Data(contentsOf: url),
            let decoded = try? JSONDecoder().decode(HighwayCodeTheoryPack.self, from: data)
        else {
            print("[Drivest iOS] highway_code_theory_load_failed")
            return .empty()
        }

        lock.lock()
        cachedTheory = decoded
        lock.unlock()
        return decoded
    }

    func loadQuestions() -> [HighwayCodeQuestion] {
        lock.lock()
        if let cachedQuestions {
            lock.unlock()
            return cachedQuestions
        }
        lock.unlock()

        guard
            let url = Bundle.main.url(
                forResource: "Drivest_QuestionBank_1200_Varied",
                withExtension: "json",
                subdirectory: "Data/highwaycode"
            ),
            let data = try? Data(contentsOf: url),
            let decoded = try? JSONDecoder().decode([HighwayCodeQuestion].self, from: data)
        else {
            print("[Drivest iOS] highway_code_questions_load_failed")
            return []
        }

        lock.lock()
        cachedQuestions = decoded
        lock.unlock()
        return decoded
    }

    func resetCache() {
        lock.lock()
        cachedTheory = nil
        cachedQuestions = nil
        lock.unlock()
    }
}
