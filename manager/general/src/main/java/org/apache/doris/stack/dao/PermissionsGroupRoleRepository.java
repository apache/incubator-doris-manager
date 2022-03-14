// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.stack.dao;

import org.apache.doris.stack.entity.PermissionsGroupRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.HashSet;
import java.util.List;

public interface PermissionsGroupRoleRepository extends
        JpaRepository<PermissionsGroupRoleEntity, Integer> {

    @Query("select s from PermissionsGroupRoleEntity s where s.clusterId = :clusterId")
    List<PermissionsGroupRoleEntity> getByClusterId(@Param("clusterId") long clusterId);

    @Query("select s from PermissionsGroupRoleEntity s where s.clusterId = :clusterId and s.groupName = :groupName")
    List<PermissionsGroupRoleEntity> getByGroupNameAndClusterId(@Param("groupName") String groupName,
                                                                @Param("clusterId") long clusterId);

    @Query("select s.groupId from PermissionsGroupRoleEntity s where s.clusterId = :clusterId")
    HashSet<Integer> getGroupIdByClusterId(@Param("clusterId") long clusterId);

    @Query("select s from PermissionsGroupRoleEntity s where s.clusterId = :clusterId and s.role = :role")
    List<PermissionsGroupRoleEntity> getByClusterIdAndRole(@Param("clusterId") long clusterId,
                                                           @Param("role") String role);

    @Query("select s from PermissionsGroupRoleEntity s where s.groupName = :groupName")
    PermissionsGroupRoleEntity getByGroupName(@Param("groupName") String groupName);

    @Modifying
    @Query("delete from PermissionsGroupRoleEntity s where s.clusterId = :clusterId")
    void deleteByClusterId(@Param("clusterId") long clusterId);

    @Modifying
    @Query("delete from PermissionsGroupRoleEntity s where s.groupName = :groupName")
    void deleteByGroupName(@Param("groupName") String groupName);

    @Query("select s from PermissionsGroupRoleEntity s where s.groupName like 'All User_%' and s.paloUserName like "
            + "'Analyzer%' and s.password = '123456'")
    List<PermissionsGroupRoleEntity> getByGroupName();

    @Query("select s from PermissionsGroupRoleEntity s where s.password is not null")
    List<PermissionsGroupRoleEntity> getByPassword();

    @Query("select s from PermissionsGroupRoleEntity s where s.role is not null and s.password "
            + "is null and s.paloUserName is null and s.groupName not like 'Administrators%' and s.groupName not like"
            + " 'All Users%'")
    List<PermissionsGroupRoleEntity> getByUserNameAndPassword();

    @Query("select s from PermissionsGroupRoleEntity s where s.clusterId = :clusterId and s.groupName not like "
            + "'Administrators%'")
    List<PermissionsGroupRoleEntity> getAllGroup(@Param("clusterId") long clusterId);
}
