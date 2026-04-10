package org.airsonic.player.repository;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.domain.entity.StarredMediaFile;
import org.airsonic.player.domain.entity.UserRating;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.*;

import java.util.ArrayList;
import java.util.List;

public class MediaFileSpecifications {

    public static Specification<MediaFile> matchCriteria(RandomSearchCriteria criteria, String username, String databaseType) {
        return (Root<MediaFile> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // base conditions
            predicates.add(cb.isTrue(root.get("present")));
            predicates.add(cb.equal(root.get("mediaType"), MediaType.MUSIC));
            predicates.add(cb.isNull(root.get("indexPath"))); // exclude indexed files

            // starred conditions
            boolean joinStarred = criteria.showStarredSongs() ^ criteria.showUnstarredSongs();
            if (joinStarred) {
                Subquery<StarredMediaFile> subquery = query.subquery(StarredMediaFile.class);
                Root<StarredMediaFile> starredRoot = subquery.from(StarredMediaFile.class);
                subquery.select(starredRoot);

                Predicate userPredicate = cb.equal(starredRoot.get("username"), username);
                Predicate mediaFilePredicate = cb.equal(starredRoot.get("mediaFile"), root);

                if (criteria.showStarredSongs()) {
                    subquery.where(cb.and(userPredicate, mediaFilePredicate));
                    predicates.add(cb.exists(subquery));
                } else if (criteria.showUnstarredSongs()) {
                    subquery.where(cb.and(userPredicate, mediaFilePredicate));
                    predicates.add(cb.not(cb.exists(subquery)));
                }
            }
            // album rating conditions
            boolean joinAlbumRating = criteria.minAlbumRating() != null || criteria.maxAlbumRating() != null;
            if (joinAlbumRating) {
                Subquery<String> albumSubquery = query.subquery(String.class);
                Root<UserRating> ratingRoot = albumSubquery.from(UserRating.class);
                Root<MediaFile> albumRoot = albumSubquery.from(MediaFile.class);
                albumSubquery.select(albumRoot.get("path"));

                List<Predicate> ratingPredicates = new ArrayList<>();
                ratingPredicates.add(cb.equal(ratingRoot.get("username"), username));
                ratingPredicates.add(cb.equal(albumRoot.get("mediaType"), MediaType.ALBUM));
                ratingPredicates.add(cb.equal(ratingRoot.get("mediaFileId"), albumRoot.get("id")));

                if (criteria.minAlbumRating() != null) {
                    ratingPredicates.add(cb.greaterThanOrEqualTo(ratingRoot.<Integer>get("rating"), criteria.minAlbumRating()));
                }
                if (criteria.maxAlbumRating() != null) {
                    ratingPredicates.add(cb.lessThanOrEqualTo(ratingRoot.<Integer>get("rating"), criteria.maxAlbumRating()));
                }

                albumSubquery.where(cb.and(ratingPredicates.toArray(new Predicate[0])));

                predicates.add(cb.in(root.get("parentPath")).value(albumSubquery));
            }

            // folder conditions
            if (!criteria.musicFolders().isEmpty()) {
                predicates.add(root.get("folder").in(criteria.musicFolders()));
            }

            // genre conditions
            if (criteria.genre() != null) {
                predicates.add(cb.equal(root.get("genre"), criteria.genre()));
            }
            // year conditions
            if (criteria.fromYear() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("year"), criteria.fromYear()));
            }
            if (criteria.toYear() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("year"), criteria.toYear()));
            }
            // format conditions
            if (criteria.format() != null) {
                predicates.add(cb.equal(root.get("format"), criteria.format()));
            }
            // last played conditions
            if (criteria.minLastPlayedDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastPlayed"), criteria.minLastPlayedDate()));
            }
            if (criteria.maxLastPlayedDate() != null) {
                if (criteria.minLastPlayedDate() == null) {
                    predicates.add(cb.or(cb.isNull(root.get("lastPlayed")),
                            cb.lessThanOrEqualTo(root.get("lastPlayed"), criteria.maxLastPlayedDate())));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(root.get("lastPlayed"), criteria.maxLastPlayedDate()));
                }
            }

            // play count conditions
            if (criteria.minPlayCount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("playCount"), criteria.minPlayCount()));
            }

            if (criteria.maxPlayCount() != null) {
                if (criteria.minPlayCount() == null) {
                    predicates.add(cb.or(cb.isNull(root.get("playCount")),
                            cb.lessThanOrEqualTo(root.get("playCount"), criteria.maxPlayCount())));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(root.get("playCount"), criteria.maxPlayCount()));
                }
            }
            String randomFunctionName = switch (databaseType.toLowerCase()) {
                case "postgresql" -> "RANDOM";
                default -> "RAND";
            };
            Expression<Double> randomFunction = cb.function(randomFunctionName, Double.class);
            query.orderBy(cb.asc(randomFunction));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
