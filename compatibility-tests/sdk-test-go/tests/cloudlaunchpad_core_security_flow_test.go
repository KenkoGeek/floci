package tests

import (
	"context"
	"testing"

	"floci-sdk-test-go/internal/testutil"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/detective"
	detectivetypes "github.com/aws/aws-sdk-go-v2/service/detective/types"
	"github.com/aws/aws-sdk-go-v2/service/inspector2"
	inspector2types "github.com/aws/aws-sdk-go-v2/service/inspector2/types"
	"github.com/aws/aws-sdk-go-v2/service/macie2"
	"github.com/aws/aws-sdk-go-v2/service/securityhub"
	securityhubtypes "github.com/aws/aws-sdk-go-v2/service/securityhub/types"
	"github.com/stretchr/testify/require"
)

func TestCloudLaunchpadCoreDelegatedSecurityFlow(t *testing.T) {
	ctx := context.Background()

	t.Run("SecurityHubManagementToDelegatedAccount", func(t *testing.T) {
		management := securityhub.NewFromConfig(accountConfig(t, "000000000101"))
		delegated := securityhub.NewFromConfig(accountConfig(t, "000000000102"))

		_, err := management.EnableOrganizationAdminAccount(ctx, &securityhub.EnableOrganizationAdminAccountInput{
			AdminAccountId: aws.String("000000000102"),
		})
		require.NoError(t, err)
		admins, err := management.ListOrganizationAdminAccounts(ctx, &securityhub.ListOrganizationAdminAccountsInput{})
		require.NoError(t, err)
		require.Len(t, admins.AdminAccounts, 1)
		require.Equal(t, "000000000102", aws.ToString(admins.AdminAccounts[0].AccountId))

		_, err = delegated.EnableSecurityHub(ctx, &securityhub.EnableSecurityHubInput{
			EnableDefaultStandards: aws.Bool(false),
		})
		require.NoError(t, err)
		_, err = delegated.UpdateOrganizationConfiguration(ctx, &securityhub.UpdateOrganizationConfigurationInput{
			AutoEnable:          aws.Bool(false),
			AutoEnableStandards: securityhubtypes.AutoEnableStandardsNone,
			OrganizationConfiguration: &securityhubtypes.OrganizationConfiguration{
				ConfigurationType: securityhubtypes.OrganizationConfigurationConfigurationTypeCentral,
			},
		})
		require.NoError(t, err)
		first, err := delegated.DescribeOrganizationConfiguration(ctx, &securityhub.DescribeOrganizationConfigurationInput{})
		require.NoError(t, err)
		require.Equal(t, securityhubtypes.OrganizationConfigurationStatusPending,
			first.OrganizationConfiguration.Status)
		second, err := delegated.DescribeOrganizationConfiguration(ctx, &securityhub.DescribeOrganizationConfigurationInput{})
		require.NoError(t, err)
		require.Equal(t, securityhubtypes.OrganizationConfigurationStatusEnabled,
			second.OrganizationConfiguration.Status)

		aggregator, err := delegated.CreateFindingAggregator(ctx, &securityhub.CreateFindingAggregatorInput{
			RegionLinkingMode: aws.String("NO_REGIONS"),
		})
		require.NoError(t, err)
		require.NotEmpty(t, aws.ToString(aggregator.FindingAggregatorArn))
		readAggregator, err := delegated.GetFindingAggregator(ctx, &securityhub.GetFindingAggregatorInput{
			FindingAggregatorArn: aggregator.FindingAggregatorArn,
		})
		require.NoError(t, err)
		require.Equal(t, "NO_REGIONS", aws.ToString(readAggregator.RegionLinkingMode))
	})

	t.Run("MacieManagementToDelegatedAccount", func(t *testing.T) {
		management := macie2.NewFromConfig(accountConfig(t, "000000000111"))
		delegated := macie2.NewFromConfig(accountConfig(t, "000000000112"))
		_, err := management.EnableOrganizationAdminAccount(ctx, &macie2.EnableOrganizationAdminAccountInput{
			AdminAccountId: aws.String("000000000112"),
		})
		require.NoError(t, err)
		_, err = delegated.EnableMacie(ctx, &macie2.EnableMacieInput{})
		require.NoError(t, err)
		session, err := delegated.GetMacieSession(ctx, &macie2.GetMacieSessionInput{})
		require.NoError(t, err)
		require.Equal(t, "ENABLED", string(session.Status))
		_, err = delegated.UpdateOrganizationConfiguration(ctx, &macie2.UpdateOrganizationConfigurationInput{
			AutoEnable: aws.Bool(true),
		})
		require.NoError(t, err)
	})

	t.Run("InspectorDelegatedAdministrator", func(t *testing.T) {
		management := inspector2.NewFromConfig(accountConfig(t, "000000000121"))
		delegated := inspector2.NewFromConfig(accountConfig(t, "000000000122"))
		_, err := management.EnableDelegatedAdminAccount(ctx, &inspector2.EnableDelegatedAdminAccountInput{
			DelegatedAdminAccountId: aws.String("000000000122"),
		})
		require.NoError(t, err)
		admins, err := management.ListDelegatedAdminAccounts(ctx, &inspector2.ListDelegatedAdminAccountsInput{})
		require.NoError(t, err)
		require.Len(t, admins.DelegatedAdminAccounts, 1)
		status, err := delegated.BatchGetAccountStatus(ctx, &inspector2.BatchGetAccountStatusInput{
			AccountIds: []string{"000000000122"},
		})
		require.NoError(t, err)
		require.Len(t, status.Accounts, 1)
		require.Equal(t, inspector2types.StatusEnabled, status.Accounts[0].State.Status)
	})

	t.Run("DetectiveOrganizationMemberTransition", func(t *testing.T) {
		management := detective.NewFromConfig(accountConfig(t, "000000000131"))
		delegated := detective.NewFromConfig(accountConfig(t, "000000000132"))
		_, err := management.EnableOrganizationAdminAccount(ctx, &detective.EnableOrganizationAdminAccountInput{
			AccountId: aws.String("000000000132"),
		})
		require.NoError(t, err)
		graphs, err := delegated.ListGraphs(ctx, &detective.ListGraphsInput{})
		require.NoError(t, err)
		require.Len(t, graphs.GraphList, 1)
		graphArn := graphs.GraphList[0].Arn
		created, err := delegated.CreateMembers(ctx, &detective.CreateMembersInput{
			GraphArn: graphArn,
			Accounts: []detectivetypes.Account{{
				AccountId:    aws.String("000000000133"),
				EmailAddress: aws.String("member@example.com"),
			}},
			DisableEmailNotification: true,
		})
		require.NoError(t, err)
		require.Len(t, created.Members, 1)
		require.Equal(t, detectivetypes.MemberStatusAcceptedButDisabled, created.Members[0].Status)
		_, err = delegated.StartMonitoringMember(ctx, &detective.StartMonitoringMemberInput{
			GraphArn:  graphArn,
			AccountId: aws.String("000000000133"),
		})
		require.NoError(t, err)
		members, err := delegated.ListMembers(ctx, &detective.ListMembersInput{GraphArn: graphArn})
		require.NoError(t, err)
		require.Equal(t, detectivetypes.MemberStatusEnabled, members.MemberDetails[0].Status)
	})
}

func accountConfig(t *testing.T, accountId string) aws.Config {
	t.Helper()
	cfg, err := config.LoadDefaultConfig(context.Background(),
		config.WithRegion("us-east-1"),
		config.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(accountId, "test", "")),
		config.WithBaseEndpoint(testutil.Endpoint()),
	)
	require.NoError(t, err)
	return cfg
}
