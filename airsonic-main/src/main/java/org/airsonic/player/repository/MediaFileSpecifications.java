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
            boolean joinStarred = criteria.isShowStarredSongs() ^ criteria.isShowUnstarredSongs();
            if (joinStarred) {
                Subquery<StarredMediaFile> subquery = query.subquery(StarredMediaFile.class);
                Root<StarredMediaFile> starredRoot = subquery.from(StarredMediaFile.class);
                subquery.select(starredRoot);

                Predicate userPredicate = cb.equal(starredRoot.get("username"), username);
                Predicate mediaFilePredicate = cb.equal(starredRoot.get("mediaFile"), root);

                if (criteria.isShowStarredSongs()) {
                    subquery.where(cb.and(userPredicate, mediaFilePredicate));
                    predicates.add(cb.exists(subquery));
                } else if (criteria.isShowUnstarredSongs()) {
                    subquery.where(cb.and(userPredicate, mediaFilePredicate));
                    predicates.add(cb.not(cb.exists(subquery)));
                }
            }
            // album rating conditions
            boolean joinAlbumRating = criteria.getMinAlbumRating() != null || criteria.getMaxAlbumRating() != null;
            if (joinAlbumRating) {
                Subquery<String> albumSubquery = query.subquery(String.class);
                Root<UserRating> ratingRoot = albumSubquery.from(UserRating.class);
                Root<MediaFile> albumRoot = albumSubquery.from(MediaFile.class);
                albumSubquery.select(albumRoot.get("path"));

                List<Predicate> ratingPredicates = new ArrayList<>();
                ratingPredicates.add(cb.equal(ratingRoot.get("username"), username));
                ratingPredicates.add(cb.equal(albumRoot.get("mediaType"), MediaType.ALBUM));
                ratingPredicates.add(cb.equal(ratingRoot.get("mediaFileId"), albumRoot.get("id")));

                if (criteria.getMinAlbumRating() != null) {
                    ratingPredicates.add(cb.greaterThanOrEqualTo(ratingRoot.<Integer>get("rating"), criteria.getMinAlbumRating()));
                }
                if (criteria.getMaxAlbumRating() != null) {
                    ratingPredicates.add(cb.lessThanOrEqualTo(ratingRoot.<Integer>get("rating"), criteria.getMaxAlbumRating()));
                }

                albumSubquery.where(cb.and(ratingPredicates.toArray(new Predicate[0])));

                predicates.add(cb.in(root.get("parentPath")).value(albumSubquery));
            }

            // folder conditions
            if (!criteria.getMusicFolders().isEmpty()) {
                predicates.add(root.get("folder").in(criteria.getMusicFolders()));
            }

            // genre conditions
            if (criteria.getGenre() != null) {
                predicates.add(cb.equal(root.get("genre"), criteria.getGenre()));
            }
            // year conditions
            if (criteria.getFromYear() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("year"), criteria.getFromYear()));
            }
            if (criteria.getToYear() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("year"), criteria.getToYear()));
            }
            // format conditions
            if (criteria.getFormat() != null) {
                predicates.add(cb.equal(root.get("format"), criteria.getFormat()));
            }
            // last played conditions
            if (criteria.getMinLastPlayedDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastPlayed"), criteria.getMinLastPlayedDate()));
            }
            if (criteria.getMaxLastPlayedDate() != null) {
                if (criteria.getMinLastPlayedDate() == null) {
                    predicates.add(cb.or(cb.isNull(root.get("lastPlayed")),
                            cb.lessThanOrEqualTo(root.get("lastPlayed"), criteria.getMaxLastPlayedDate())));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(root.get("lastPlayed"), criteria.getMaxLastPlayedDate()));
                }
            }

            // play count conditions
            if (criteria.getMinPlayCount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("playCount"), criteria.getMinPlayCount()));
            }

            if (criteria.getMaxPlayCount() != null) {
                if (criteria.getMinPlayCount() == null) {
                    predicates.add(cb.or(cb.isNull(root.get("playCount")),
                            cb.lessThanOrEqualTo(root.get("playCount"), criteria.getMaxPlayCount())));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(root.get("playCount"), criteria.getMaxPlayCount()));
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
