package dev.grindtrack.todo.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, Long> {

  /** Open items first, then manual order — the order the list is rendered in. */
  List<Todo> findAllByOrderByDoneAscSortOrderAscIdAsc();

  List<Todo> findByKindOrderByDoneAscSortOrderAscIdAsc(String kind);
}
