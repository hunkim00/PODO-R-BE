package be.podor.notice.repository;

import be.podor.notice.model.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findByOrderByCreatedAtDesc(PageRequest pageRequest);
}
