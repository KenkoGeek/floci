package tests

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/cloudformation"
	cloudformationtypes "github.com/aws/aws-sdk-go-v2/service/cloudformation/types"
	"github.com/aws/aws-sdk-go-v2/service/organizations"
	organizationtypes "github.com/aws/aws-sdk-go-v2/service/organizations/types"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
	"github.com/stretchr/testify/require"
)

func TestCloudLaunchpadCoreServiceManagedStackSetsFlow(t *testing.T) {
	ctx := context.Background()
	managementAccount := fmt.Sprintf("%012d", time.Now().UnixNano()%1_000_000_000_000)
	managementCfg := accountConfig(t, managementAccount)
	org := organizations.NewFromConfig(managementCfg)
	cfn := cloudformation.NewFromConfig(managementCfg)

	createdOrg, err := org.CreateOrganization(ctx, &organizations.CreateOrganizationInput{FeatureSet: "ALL"})
	require.NoError(t, err)
	require.Equal(t, managementAccount, aws.ToString(createdOrg.Organization.MasterAccountId))

	roots, err := org.ListRoots(ctx, &organizations.ListRootsInput{})
	require.NoError(t, err)
	require.Len(t, roots.Roots, 1)
	rootID := aws.ToString(roots.Roots[0].Id)

	parent, err := org.CreateOrganizationalUnit(ctx, &organizations.CreateOrganizationalUnitInput{
		ParentId: aws.String(rootID),
		Name:     aws.String("CoreStackSetsParent"),
	})
	require.NoError(t, err)
	parentID := aws.ToString(parent.OrganizationalUnit.Id)
	child, err := org.CreateOrganizationalUnit(ctx, &organizations.CreateOrganizationalUnitInput{
		ParentId: aws.String(parentID),
		Name:     aws.String("CoreStackSetsChild"),
	})
	require.NoError(t, err)
	childID := aws.ToString(child.OrganizationalUnit.Id)

	directID := createOrganizationAccount(t, ctx, org, rootID, parentID,
		"core-stacksets-direct-"+managementAccount+"@example.com", "CoreStackSetsDirect")
	nestedID := createOrganizationAccount(t, ctx, org, rootID, childID,
		"core-stacksets-nested-"+managementAccount+"@example.com", "CoreStackSetsNested")

	_, err = cfn.ActivateOrganizationsAccess(ctx, &cloudformation.ActivateOrganizationsAccessInput{})
	require.NoError(t, err)
	access, err := org.ListAWSServiceAccessForOrganization(ctx, &organizations.ListAWSServiceAccessForOrganizationInput{})
	require.NoError(t, err)
	require.Contains(t, servicePrincipals(access.EnabledServicePrincipals), "stacksets.cloudformation.amazonaws.com")

	stackSetName := "core-service-managed-" + managementAccount
	queueName := "core-sm-q-" + managementAccount
	_, err = cfn.CreateStackSet(ctx, &cloudformation.CreateStackSetInput{
		StackSetName:    aws.String(stackSetName),
		TemplateBody:    aws.String(fmt.Sprintf(`{"Resources":{"Q":{"Type":"AWS::SQS::Queue","Properties":{"QueueName":"%s"}}}}`, queueName)),
		PermissionModel: "SERVICE_MANAGED",
		AutoDeployment:  &cloudformationtypes.AutoDeployment{Enabled: aws.Bool(true)},
	})
	require.NoError(t, err)

	createdInstances, err := cfn.CreateStackInstances(ctx, &cloudformation.CreateStackInstancesInput{
		StackSetName: aws.String(stackSetName),
		DeploymentTargets: &cloudformationtypes.DeploymentTargets{
			OrganizationalUnitIds: []string{parentID},
		},
		Regions: []string{"us-east-1"},
	})
	require.NoError(t, err)
	require.NotEmpty(t, aws.ToString(createdInstances.OperationId))

	instances, err := cfn.ListStackInstances(ctx, &cloudformation.ListStackInstancesInput{StackSetName: aws.String(stackSetName)})
	require.NoError(t, err)
	require.Len(t, instances.Summaries, 2)
	seen := map[string]string{}
	for _, summary := range instances.Summaries {
		seen[aws.ToString(summary.Account)] = aws.ToString(summary.OrganizationalUnitId)
	}
	require.Equal(t, parentID, seen[directID])
	require.Equal(t, parentID, seen[nestedID])

	assertQueueExists(t, ctx, directID, queueName)
	assertQueueExists(t, ctx, nestedID, queueName)
}

func createOrganizationAccount(t *testing.T, ctx context.Context, org *organizations.Client,
	rootID, destinationID, email, name string) string {
	t.Helper()
	created, err := org.CreateAccount(ctx, &organizations.CreateAccountInput{
		Email:       aws.String(email),
		AccountName: aws.String(name),
	})
	require.NoError(t, err)
	require.Equal(t, "SUCCEEDED", string(created.CreateAccountStatus.State))
	accountID := aws.ToString(created.CreateAccountStatus.AccountId)
	require.NotEmpty(t, accountID)
	_, err = org.MoveAccount(ctx, &organizations.MoveAccountInput{
		AccountId:           aws.String(accountID),
		SourceParentId:      aws.String(rootID),
		DestinationParentId: aws.String(destinationID),
	})
	require.NoError(t, err)
	return accountID
}

func servicePrincipals(principals []organizationtypes.EnabledServicePrincipal) []string {
	result := make([]string, 0, len(principals))
	for _, principal := range principals {
		result = append(result, aws.ToString(principal.ServicePrincipal))
	}
	return result
}

func assertQueueExists(t *testing.T, ctx context.Context, accountID, queueName string) {
	t.Helper()
	client := sqs.NewFromConfig(accountConfig(t, accountID))
	queue, err := client.GetQueueUrl(ctx, &sqs.GetQueueUrlInput{QueueName: aws.String(queueName)})
	require.NoError(t, err)
	require.Contains(t, aws.ToString(queue.QueueUrl), "/"+accountID+"/"+queueName)
}
