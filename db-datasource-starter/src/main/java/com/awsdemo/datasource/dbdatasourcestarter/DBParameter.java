package com.awsdemo.datasource.dbdatasourcestarter;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DBParameter {
    private String host;
    private String username;
    private String password;
}
