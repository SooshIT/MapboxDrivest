import Foundation

@MainActor
final class TheoryPackLoader {
    static let shared = TheoryPackLoader()

    private let lock = NSLock()
    private var cachedPack: TheoryPack?

    func load() -> TheoryPack {
        lock.lock()
        if let cachedPack {
            lock.unlock()
            return cachedPack
        }
        lock.unlock()

        guard
            let url = Bundle.main.url(
                forResource: "theory_pack_v1",
                withExtension: "json",
                subdirectory: "Data/theory"
            ),
            let data = try? Data(contentsOf: url),
            let pack = try? JSONDecoder().decode(TheoryPack.self, from: data),
            !pack.topics.isEmpty
        else {
            print("[Drivest iOS] theory_pack_load_failed")
            return .empty()
        }

        lock.lock()
        cachedPack = pack
        lock.unlock()
        return pack
    }

    func resetCache() {
        lock.lock()
        cachedPack = nil
        lock.unlock()
    }
}
