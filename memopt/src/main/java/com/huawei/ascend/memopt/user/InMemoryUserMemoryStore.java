package com.huawei.ascend.memopt.user;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-process {@link UserMemoryStore} for offline eval. A single map partitioned by
 * {@link MemoryScope#key()} (not a table per user), with a per-scope cap that
 * evicts the oldest facts — the cost lever for bank-scale user counts. Search is a
 * keyword overlap score (the closed engine does semantic ranking); good enough to
 * verify the kit and scope isolation offline.
 */
public final class InMemoryUserMemoryStore implements UserMemoryStore {

    private final ConcurrentMap<String, List<MemoryRecord>> store = new ConcurrentHashMap<>();
    private final int maxFactsPerScope;

    public InMemoryUserMemoryStore() {
        this(50);
    }

    public InMemoryUserMemoryStore(int maxFactsPerScope) {
        this.maxFactsPerScope = Math.max(1, maxFactsPerScope);
    }

    @Override
    public List<MemoryHit> search(MemoryScope scope, String query, int limit) {
        if (limit <= 0 || query == null || query.isBlank()) {
            return List.of();
        }
        List<MemoryRecord> facts = store.get(scope.key());
        if (facts == null) {
            return List.of();
        }
        Set<String> queryTokens = tokens(query);
        List<MemoryHit> hits = new ArrayList<>();
        synchronized (facts) {
            for (MemoryRecord r : facts) {
                long overlap = tokens(r.content()).stream().filter(queryTokens::contains).count();
                if (overlap > 0) {
                    hits.add(new MemoryHit(r.content(), r.kind(), overlap));
                }
            }
        }
        hits.sort(Comparator.comparingDouble(MemoryHit::score).reversed());
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    @Override
    public void save(MemoryScope scope, List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<MemoryRecord> facts = store.computeIfAbsent(scope.key(), k -> new ArrayList<>());
        synchronized (facts) {
            for (MemoryRecord r : records) {
                if (r == null || r.content().isBlank()) {
                    continue;
                }
                facts.removeIf(existing -> existing.content().equals(r.content())); // dedupe by content
                facts.add(r);
            }
            // Cost cap: keep only the newest maxFactsPerScope, evict oldest.
            while (facts.size() > maxFactsPerScope) {
                facts.remove(0);
            }
        }
    }

    @Override
    public void forget(MemoryScope scope) {
        store.remove(scope.key());
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : text.toLowerCase(Locale.ROOT).split("[^a-z0-9\\u4e00-\\u9fff]+")) {
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }
}
