package com.retail.branches;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name="branches")
public class BranchEntity {
 @Id private UUID id;
 @Column(nullable=false,unique=true,length=30) private String code;
 @Column(nullable=false,length=120) private String name;
 @Column(columnDefinition="text") private String address;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected BranchEntity(){}
 public BranchEntity(UUID id,String code,String name,String address){this.id=id;this.code=code;this.name=name;this.address=address;this.active=true;}
 @PrePersist void onCreate(){var now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getAddress(){return address;} public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
