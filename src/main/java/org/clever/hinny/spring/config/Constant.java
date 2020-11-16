package org.clever.hinny.spring.config;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/09/11 21:22 <br/>
 */
public interface Constant {

    String Config_Root = "clever.hinny";

    String Config_Mvc_Handler = Config_Root + ".mvc-handler";

    String Config_Engine_Instance_Pool = Config_Root + ".engine-instance-pool";

    String Config_MyBatis_Mapper_Config = Config_Root + ".mybatis-mapper-config";

    String Config_Multiple_Jdbc_Config = Config_Root + ".multiple-jdbc";

    String Config_Multiple_Redis_Config = Config_Root + ".multiple-redis";
}
