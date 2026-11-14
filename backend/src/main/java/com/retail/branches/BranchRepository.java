package com.retail.branches;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchRepository extends JpaRepository<BranchEntity,UUID> { List<BranchEntity> findAllByActiveTrueOrderByNameAsc(); }
