package org.aps.export_data_v2.repository;

import org.aps.export_data_v2.entity.Salary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryRepository extends JpaRepository<Salary, Integer> {
    @Query(value = "SELECT * FROM salaries ORDER BY emp_no LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Salary> findAllByOffsetRange(
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
