package com.ecoloop.rewards;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RewardCatalogRepository extends JpaRepository<RewardCatalogItem,UUID>{List<RewardCatalogItem> findAllByActiveTrue();}
