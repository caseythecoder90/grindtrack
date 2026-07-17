package dev.grindtrack.tracking.domain;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, LocalDate> {}
