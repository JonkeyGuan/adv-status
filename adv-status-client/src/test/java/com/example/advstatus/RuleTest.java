package com.example.advstatus;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.ExecutionResults;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieServiceResponse.ResponseType;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.RuleServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleTest {

    static final Logger log = LoggerFactory.getLogger(RuleTest.class);

    private static final String droolsUrl = "http://localhost:8080/kie-server/services/rest/server";
    private static final String username = "rhdmAdmin";
    private static final String password = "admin@123";

    private static final MarshallingFormat FORMAT = MarshallingFormat.JSON;

    private static KieServicesConfiguration kieServicesConfig;
    private static KieServicesClient kieServicesClient;

    @Before
    public void setup() {
        kieServicesConfig = KieServicesFactory.newRestConfiguration(droolsUrl, username, password);
        kieServicesConfig.setMarshallingFormat(FORMAT);

        Set<Class<?>> allClasses = new HashSet<Class<?>>();
        allClasses.add(Campaign.class);
        kieServicesConfig.addExtraClasses(allClasses);

        kieServicesClient = KieServicesFactory.newKieServicesClient(kieServicesConfig);
    }

    @After
    public void teardown() {

    }

    @Test
    public void test_continue_flow() {
        Campaign result = execution(new Campaign(false, 3.7, 1, ""));
        assertEquals("开启广告/维持投放不变", result.getResult());
    }

    @Test
    public void test_pause_1_flow() {
        Campaign result = execution(new Campaign(true, 3.7, 1, ""));
        assertEquals("暂停广告", result.getResult());
    }

    @Test
    public void test_pause_2_flow() {
        Campaign result = execution(new Campaign(false, 3.6, 1, ""));
        assertEquals("暂停广告", result.getResult());
    }

    @Test
    public void test_pause_3_flow() {
        Campaign result = execution(new Campaign(false, 3.7, 0, ""));
        assertEquals("暂停广告", result.getResult());
    }

    public Campaign execution(Campaign fact) {

        Campaign result = null;

        RuleServicesClient rulesClient = kieServicesClient.getServicesClient(RuleServicesClient.class);

        KieCommands commandFactory = KieServices.Factory.get().getCommands();

        List<Command<?>> commands = new ArrayList<>();

        commands.add(commandFactory.newInsert(fact));

        Command<?> fireAllRules = commandFactory.newFireAllRules();
        Command<?> process = commandFactory.newStartProcess("AdvStatus.Adjust");
        Command<?> getObjectsCampaign = commandFactory.newGetObjects(new ClassObjectFilter(Campaign.class),
                "Campaign");
        Command<?> dispose = commandFactory.newDispose();
        commands.addAll(Arrays.asList(fireAllRules, process, getObjectsCampaign, dispose));

        Command<?> batchCommand = commandFactory.newBatchExecution(commands);
        ServiceResponse<ExecutionResults> executeResponse = rulesClient.executeCommandsWithResults("AdvStatus",
                batchCommand);

        if (executeResponse.getType() == ResponseType.SUCCESS) {
            @SuppressWarnings("unchecked")
            List<Campaign> campaigns = (List<Campaign>) executeResponse.getResult().getValue("Campaign");
            for (Campaign c : campaigns) {
                log.info("" + c);
            }

            if (campaigns.size() > 0) {
                result = campaigns.get(0);
            }
        }

        return result;
    }

}