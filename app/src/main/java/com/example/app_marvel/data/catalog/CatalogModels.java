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
    public static final class Movie {
        public final int id, publisherId, runtime;
        public final String title, imageUrl, siteUrl;
        public Movie(int id, int publisherId, String title, int runtime, String imageUrl, String siteUrl) {
            this.id=id; this.publisherId=publisherId; this.title=title; this.runtime=runtime; this.imageUrl=imageUrl; this.siteUrl=siteUrl;
        }
    }
    public static final class MovieDetails {
        public final Movie movie;
        public final String rating, distributor, originalDeck, originalDescription;
        public final List<Reference> characters, teams;
        public final List<Credit> studios, producers, writers, locations, objects, concepts;
        public MovieDetails(Movie movie,String rating,String distributor,String originalDeck,String originalDescription,
                List<Reference> characters,List<Reference> teams,List<Credit> studios,List<Credit> producers,List<Credit> writers,
                List<Credit> locations,List<Credit> objects,List<Credit> concepts) {
            this.movie=movie;this.rating=rating;this.distributor=distributor;this.originalDeck=originalDeck;this.originalDescription=originalDescription;
            this.characters=immutable(characters);this.teams=immutable(teams);this.studios=immutable(studios);
            this.producers=immutable(producers);this.writers=immutable(writers);this.locations=immutable(locations);
            this.objects=immutable(objects);this.concepts=immutable(concepts);
        }
        public List<Reference> relations(String kind) {
            if (kind.equals("characters")) return characters;
            if (kind.equals("teams")) return teams;
            throw new IllegalArgumentException("Relação desconhecida");
        }
    }
    public static final class MoviesPage {
        public final List<Movie> items;
        public final int nextOffset;
        public final boolean hasMore;
        public MoviesPage(List<Movie> items,int nextOffset,boolean hasMore) {
            this.items=immutable(items); this.nextOffset=nextOffset; this.hasMore=hasMore;
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
    public static final class Credit {
        public final Reference reference;
        public final String role, siteUrl;
        public Credit(Reference reference, String role, String siteUrl) {
            this.reference = reference; this.role = role; this.siteUrl = siteUrl;
        }
    }
    public static final class IssueDetails {
        public final Issue issue;
        public final int publisherId;
        public final String name, coverDate, originalDeck, originalDescription, volumeSiteUrl;
        public final List<Reference> characters, teams;
        public final List<Credit> creators, arcs, locations, objects, concepts;
        public IssueDetails(Issue issue, int publisherId, String name, String coverDate, String originalDeck,
                String originalDescription, String volumeSiteUrl, List<Reference> characters, List<Reference> teams,
                List<Credit> creators, List<Credit> arcs, List<Credit> locations, List<Credit> objects, List<Credit> concepts) {
            this.issue = issue; this.publisherId = publisherId; this.name = name; this.coverDate = coverDate;
            this.originalDeck = originalDeck; this.originalDescription = originalDescription; this.volumeSiteUrl = volumeSiteUrl;
            this.characters = immutable(characters); this.teams = immutable(teams); this.creators = immutable(creators);
            this.arcs = immutable(arcs); this.locations = immutable(locations); this.objects = immutable(objects); this.concepts = immutable(concepts);
        }
        public List<Reference> relations(String kind) {
            if (kind.equals("characters")) return characters;
            if (kind.equals("teams")) return teams;
            throw new IllegalArgumentException("Relação desconhecida");
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
    public static final class ComicsCursor {
        public final String date;
        public final int offset, examined;
        public ComicsCursor(String date, int offset, int examined) { this.date = date; this.offset = offset; this.examined = examined; }
        public static ComicsCursor start() { return new ComicsCursor("", -1, 0); }
    }
    public static final class ComicsPage {
        public final List<Issue> items;
        public final ComicsCursor nextCursor;
        public final int nextOffset;
        public final boolean hasMore;
        public ComicsPage(List<Issue> items, ComicsCursor nextCursor, boolean hasMore) {
            this.items = immutable(items); this.nextCursor = nextCursor; this.nextOffset = nextCursor.examined; this.hasMore = hasMore;
        }
    }
    public static final class StoryArc {
        public final int id, publisherId;
        public final String name, imageUrl, siteUrl;
        public StoryArc(int id, int publisherId, String name, String imageUrl, String siteUrl) {
            this.id=id; this.publisherId=publisherId; this.name=name; this.imageUrl=imageUrl; this.siteUrl=siteUrl;
        }
    }
    public static final class ArcDetails {
        public final StoryArc arc;
        public final String aliases, originalDeck, originalDescription;
        public final List<Reference> issues;
        public ArcDetails(StoryArc arc,String aliases,String originalDeck,String originalDescription,List<Reference> issues) {
            this.arc=arc; this.aliases=aliases; this.originalDeck=originalDeck; this.originalDescription=originalDescription;
            this.issues=immutable(issues);
        }
    }
    public static final class ArcsPage {
        public final List<StoryArc> items;
        public final int nextOffset;
        public final boolean hasMore;
        public ArcsPage(List<StoryArc> items, int nextOffset, boolean hasMore) {
            this.items=immutable(items); this.nextOffset=nextOffset; this.hasMore=hasMore;
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
