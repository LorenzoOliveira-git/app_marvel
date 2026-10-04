package com.example.app_marvel.data.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CatalogModels {
    private CatalogModels() { }
    public static final class Reference {
        public final int id;
        public final String name, path;
        public Reference(int id, String name, String path) { this.id = id; this.name = name; this.path = path; }
    }
    public static final class Character {
        public final int id, publisherId, originId, gender;
        public final String name, realName, origin, originalDeck, imageUrl, siteUrl, aliases;
        public Character(int id, int publisherId, String name, String realName, int originId, String origin,
                         int gender, String originalDeck, String imageUrl, String siteUrl, String aliases) {
            this.id = id; this.publisherId = publisherId; this.name = name; this.realName = realName;
            this.originId = originId; this.origin = origin; this.gender = gender;
            this.originalDeck = originalDeck; this.imageUrl = imageUrl; this.siteUrl = siteUrl; this.aliases = aliases;
        }
    }
    public static final class Issue {
        public final int id, volumeId;
        public final String title, volume, publicationDate, imageUrl, siteUrl;
        public Issue(int id, int volumeId, String title, String volume, String publicationDate, String imageUrl, String siteUrl) {
            this.id = id; this.volumeId = volumeId; this.title = title; this.volume = volume;
            this.publicationDate = publicationDate; this.imageUrl = imageUrl; this.siteUrl = siteUrl;
        }
    }
    public static final class CharacterDetails {
        public final Character character;
        public final int appearanceCount;
        public final Reference firstAppearance;
        public final List<Reference> powers, teams, friends, enemies;
        public CharacterDetails(Character character, int appearanceCount, Reference firstAppearance,
                List<Reference> powers, List<Reference> teams, List<Reference> friends, List<Reference> enemies) {
            this.character = character; this.appearanceCount = appearanceCount; this.firstAppearance = firstAppearance;
            this.powers = immutable(powers); this.teams = immutable(teams); this.friends = immutable(friends); this.enemies = immutable(enemies);
        }
        public List<Reference> relations(String kind) {
            switch (kind) { case "teams": return teams; case "friends": return friends; case "enemies": return enemies;
                default: throw new IllegalArgumentException("Relação desconhecida"); }
        }
    }
    public static final class RelatedItem {
        public final int id, publisherId;
        public final String name, imageUrl, siteUrl;
        public RelatedItem(int id, int publisherId, String name, String imageUrl, String siteUrl) {
            this.id = id; this.publisherId = publisherId; this.name = name; this.imageUrl = imageUrl; this.siteUrl = siteUrl;
        }
    }
    public static final class RelationPage {
        public final List<RelatedItem> items;
        public final int nextOffset;
        public final boolean hasMore;
        public RelationPage(List<RelatedItem> items, int nextOffset, boolean hasMore) {
            this.items = immutable(items); this.nextOffset = nextOffset; this.hasMore = hasMore;
        }
    }
    public static final class AppearanceIndex {
        public final int characterId, publisherId;
        public final List<Reference> issues;
        public AppearanceIndex(int characterId, int publisherId, List<Reference> issues) {
            this.characterId = characterId; this.publisherId = publisherId; this.issues = immutable(issues);
        }
    }
    public static final class AppearancePage {
        public final List<Issue> items;
        public final int nextOffset;
        public final boolean hasMore;
        public AppearancePage(List<Issue> items, int nextOffset, boolean hasMore) {
            this.items = immutable(items); this.nextOffset = nextOffset; this.hasMore = hasMore;
        }
    }
    public static final class ComicsPage {
        public final List<Issue> items;
        public final int nextOffset;
        public final boolean hasMore;
        public ComicsPage(List<Issue> items, int nextOffset, boolean hasMore) {
            this.items = immutable(items); this.nextOffset = nextOffset; this.hasMore = hasMore;
        }
    }
    private static <T> List<T> immutable(List<T> items) { return Collections.unmodifiableList(new ArrayList<>(items)); }
    public static final class Page {
        public final List<Character> characters;
        public final int nextOffset;
        public final boolean hasMore;
        public Page(List<Character> characters, int nextOffset, boolean hasMore) {
            this.characters = Collections.unmodifiableList(new ArrayList<>(characters));
            this.nextOffset = nextOffset; this.hasMore = hasMore;
        }
    }
    public enum Failure { CONNECTION, DATA, STORAGE }
    public static final class Result<T> {
        public final T data;
        public final Failure failure;
        private Result(T data, Failure failure) { this.data = data; this.failure = failure; }
        public static <T> Result<T> success(T data) { return new Result<>(data, null); }
        public static <T> Result<T> failed(Failure failure) { return new Result<>(null, failure); }
    }
    public interface Callback<T> { void complete(Result<T> result); }
}
