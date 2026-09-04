package tests

import (
	"context"
	"testing"

	"floci-sdk-test-go/internal/testutil"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/accessanalyzer"
	accessanalyzertypes "github.com/aws/aws-sdk-go-v2/service/accessanalyzer/types"
	"github.com/aws/aws-sdk-go-v2/service/account"
	accounttypes "github.com/aws/aws-sdk-go-v2/service/account/types"
	"github.com/aws/aws-sdk-go-v2/service/budgets"
	budgetstypes "github.com/aws/aws-sdk-go-v2/service/budgets/types"
	"github.com/aws/aws-sdk-go-v2/service/cloudformation"
	cloudformationtypes "github.com/aws/aws-sdk-go-v2/service/cloudformation/types"
	"github.com/aws/aws-sdk-go-v2/service/cloudwatchlogs"
	cloudwatchlogstypes "github.com/aws/aws-sdk-go-v2/service/cloudwatchlogs/types"
	"github.com/aws/aws-sdk-go-v2/service/controltower"
	controltowertypes "github.com/aws/aws-sdk-go-v2/service/controltower/types"
	"github.com/aws/aws-sdk-go-v2/service/detective"
	detectivetypes "github.com/aws/aws-sdk-go-v2/service/detective/types"
	"github.com/aws/aws-sdk-go-v2/service/guardduty"
	guarddutytypes "github.com/aws/aws-sdk-go-v2/service/guardduty/types"
	"github.com/aws/aws-sdk-go-v2/service/identitystore"
	identitystoretypes "github.com/aws/aws-sdk-go-v2/service/identitystore/types"
	"github.com/aws/aws-sdk-go-v2/service/macie2"
	macie2types "github.com/aws/aws-sdk-go-v2/service/macie2/types"
	"github.com/aws/aws-sdk-go-v2/service/securityhub"
	securityhubtypes "github.com/aws/aws-sdk-go-v2/service/securityhub/types"
	"github.com/aws/aws-sdk-go-v2/service/servicequotas"
	servicequotastypes "github.com/aws/aws-sdk-go-v2/service/servicequotas/types"
	"github.com/aws/aws-sdk-go-v2/service/ssoadmin"
	ssotypes "github.com/aws/aws-sdk-go-v2/service/ssoadmin/types"
	"github.com/stretchr/testify/require"
)

func TestCloudLaunchpadCoreAwsSdkErrors(t *testing.T) {
	ctx := context.Background()
	cfg := testutil.Config()

	t.Run("SSOAdminConflictException", func(t *testing.T) {
		client := ssoadmin.NewFromConfig(cfg)
		instances, err := client.ListInstances(ctx, &ssoadmin.ListInstancesInput{})
		require.NoError(t, err)
		require.Len(t, instances.Instances, 1)
		input := &ssoadmin.CreatePermissionSetInput{
			InstanceArn: instances.Instances[0].InstanceArn,
			Name:        aws.String("CoreSdkDuplicate"),
		}
		_, err = client.CreatePermissionSet(ctx, input)
		require.NoError(t, err)
		_, err = client.CreatePermissionSet(ctx, input)
		var typed *ssotypes.ConflictException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("IdentityStoreResourceNotFoundException", func(t *testing.T) {
		client := identitystore.NewFromConfig(cfg)
		_, err := client.CreateGroupMembership(ctx, &identitystore.CreateGroupMembershipInput{
			IdentityStoreId: aws.String("d-9067f2a3c1"),
			GroupId:         aws.String("11111111-1111-4111-8111-111111111111"),
			MemberId:        &identitystoretypes.MemberIdMemberUserId{Value: "22222222-2222-4222-8222-222222222222"},
		})
		var typed *identitystoretypes.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("AccountResourceNotFoundException", func(t *testing.T) {
		client := account.NewFromConfig(cfg)
		_, err := client.GetAlternateContact(ctx, &account.GetAlternateContactInput{
			AlternateContactType: accounttypes.AlternateContactTypeSecurity,
		})
		var typed *accounttypes.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("AccessAnalyzerResourceNotFoundException", func(t *testing.T) {
		client := accessanalyzer.NewFromConfig(cfg)
		_, err := client.DeleteAnalyzer(ctx, &accessanalyzer.DeleteAnalyzerInput{AnalyzerName: aws.String("missing-analyzer")})
		var typed *accessanalyzertypes.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("BudgetsNotFoundException", func(t *testing.T) {
		client := budgets.NewFromConfig(cfg)
		_, err := client.DescribeBudget(ctx, &budgets.DescribeBudgetInput{
			AccountId:  aws.String("000000000000"),
			BudgetName: aws.String("missing-budget"),
		})
		var typed *budgetstypes.NotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("ServiceQuotasNoSuchResourceException", func(t *testing.T) {
		client := servicequotas.NewFromConfig(cfg)
		_, err := client.GetServiceQuota(ctx, &servicequotas.GetServiceQuotaInput{
			ServiceCode: aws.String("organizations"),
			QuotaCode:   aws.String("L-DOESNOTEXIST"),
		})
		var typed *servicequotastypes.NoSuchResourceException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("SecurityHubResourceNotFoundException", func(t *testing.T) {
		client := securityhub.NewFromConfig(cfg)
		_, err := client.DescribeHub(ctx, &securityhub.DescribeHubInput{})
		var typed *securityhubtypes.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("MacieResourceNotFoundException", func(t *testing.T) {
		client := macie2.NewFromConfig(cfg)
		_, err := client.GetMacieSession(ctx, &macie2.GetMacieSessionInput{})
		var typed *macie2types.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("DetectiveResourceNotFoundException", func(t *testing.T) {
		client := detective.NewFromConfig(cfg)
		_, err := client.ListMembers(ctx, &detective.ListMembersInput{
			GraphArn: aws.String("arn:aws:detective:us-east-1:000000000000:graph:00000000000000000000000000000000"),
		})
		var typed *detectivetypes.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("GuardDutyBadRequestException", func(t *testing.T) {
		client := guardduty.NewFromConfig(cfg)
		_, err := client.CreateMembers(ctx, &guardduty.CreateMembersInput{
			DetectorId: aws.String("00000000000000000000000000000000"),
			AccountDetails: []guarddutytypes.AccountDetail{{
				AccountId: aws.String("111111111111"),
				Email:     aws.String("member@example.com"),
			}},
		})
		var typed *guarddutytypes.BadRequestException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("CloudWatchLogsResourceNotFoundException", func(t *testing.T) {
		client := cloudwatchlogs.NewFromConfig(cfg)
		_, err := client.DescribeAccountPolicies(ctx, &cloudwatchlogs.DescribeAccountPoliciesInput{
			PolicyType: cloudwatchlogstypes.PolicyTypeSubscriptionFilterPolicy,
			PolicyName: aws.String("missing-policy"),
		})
		var typed *cloudwatchlogstypes.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("CloudFormationStackSetNotFoundException", func(t *testing.T) {
		client := cloudformation.NewFromConfig(cfg)
		_, err := client.DescribeStackSet(ctx, &cloudformation.DescribeStackSetInput{StackSetName: aws.String("missing-stackset")})
		var typed *cloudformationtypes.StackSetNotFoundException
		require.ErrorAs(t, err, &typed)
	})

	t.Run("ControlTowerResourceNotFoundException", func(t *testing.T) {
		client := controltower.NewFromConfig(cfg)
		_, err := client.GetLandingZoneOperation(ctx, &controltower.GetLandingZoneOperationInput{
			OperationIdentifier: aws.String("11111111-1111-4111-8111-111111111111"),
		})
		var typed *controltowertypes.ResourceNotFoundException
		require.ErrorAs(t, err, &typed)
	})
}
