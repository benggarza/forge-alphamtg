import torch
from torch import nn


# TODO: we need 2 separate models:
# one model that encodes the game state s_0 into a determined-size hidden state
# one model that takes an encoded game state [and action] and predicts the state value [and action prob and resultant encoded state]

class AlphaMTG:
    def __init__(self, num_players):
        self.num_players = num_players
        # self.hand_encoders = {}
        # self.battlefield_encoders = {}
        # self.graveyard_encoders = {}
        # self.exile_encoders = {}
        # for player in range(num_players):
        #    self.hand_encoders[player] = Zone_Encoder()
        #    self.battlefield_encoders[player] = Zone_Encoder()
        #    self.graveyard_encoders[player] = Zone_Encoder()
        #    self.exile_encoders[player] = Zone_Encoder()
        # seems like we can get away with having a single encoder for each zone to share among all players
        self.hand_encoder = ZoneEncoder()
        self.battlefield_encoder = ZoneEncoder()
        self.library_encoder = ZoneEncoder()
        self.graveyard_encoder = ZoneEncoder()
        self.exile_encoder = ZoneEncoder()
        self.stack_encoder = ZoneEncoder()

        # necessary?
        # self.rules_embedding = Rules_Embedding()

        # there are 4*n + 1 zones, each with an output of size 2*hidden_state
        self.fc1 = nn.Linear(in_features=(4 * num_players + 1) * 100, out_features=512)
        self.fc2 = nn.Linear(in_features=512, out_features=256)
        self.fc3 = nn.Linear(in_features=256, out_features=128)

        # the predicted value of the current state
        self.value_out = nn.Linear(in_features=128, out_features=1)

        # the linear layers that evaluate an action for the resultant state and model the policy function
        # TODO: what is the input size? it's the output of fc3 + whatever the size of the action input is
        self.fc4 = nn.Linear(in_features=128 + 100, out_features=64)
        self.fc5 = nn.Linear(in_features=64, out_features=32)

    # TODO: it is unclear at the moment what the format of the input will be to this model...
    # likely not a perfect fit with the nn.module expectations
    # this is more pseudocode than real code at the moment

    # game state structure idea:
    # game_state : {
    #  player : {
    #   life: int,
    #   poison_counters: int,
    #   ... ,
    #   hand : [
    #    {
    #     card_name: str,
    #     power: int,
    #     toughness: int,
    #     rules_text: str,
    #     supertype: str,
    #     player_controller: int,
    #     player_owner: int,
    #     ...
    #    }, {}, ...
    #   ],
    #   battlefield: [ {}, ...],
    #   graveyard: [ {}, ...],
    #   exile: [ {}, ...]
    #  },
    #  stack: [ {}, ...]
    # }
    def forward(self, state_action):
        player_states = []
        zones_encoded = []
        action = state_action["action"]
        state = state_action["state"]
        for player in range(self.num_players):
            player_zones = state[player]
            hand = player_zones["hand"]
            battlefield = player_zones["battlefield"]
            graveyard = player_zones["graveyard"]
            exile = player_zones["exile"]
            library = player_zones["library"]
            # add the player state to player_states
            player_states.append(
                [
                    player_zones["life"],
                    player_zones["poison_counters"],
                ]
            )
            # for each zone
            for zone, encoder in zip(
                [hand, battlefield, graveyard, exile],
                [
                    self.hand_encoder,
                    self.battlefield_encoder,
                    self.graveyard_encoder,
                    self.exile_encoder,
                    self.library_encoder
                ],
            ):
                # generate the sequence of game objects in the zone
                zone_embed = []
                for game_object in zone:
                    game_object_embed = []
                    # unpack the game object and pass text through word embedding
                    for key, val in game_object.items():
                        embed_val = val
                        # get the word embedding of the rules text
                        # rules likely not necessary
                        # if key == "rules_text":
                        #    embed_val = self.rules_embedding(val)
                        game_object_embed.append(*embed_val)
                    zone_embed.append(game_object_embed)
                # pass the game object sequence to the LSTM encoder
                zone_encoded = encoder(zone_embed)
                # add to the encoded zones
                zones_encoded.append(zone_encoded)

        ## same process as above but for the shared stack
        stack = state["stack"]
        stack_embed = []
        for game_object in stack:
            game_object_embed = []
            for key, val in game_object.items():
                embed_val = val
                #if key == "rules_text":
                #    embed_val = self.rules_embedding(val)
                game_object_embed.append(*embed_val)
            stack_embed.append(game_object_embed)
        stack_encoded = self.stack_encoder(stack_embed)
        zones_encoded.append(stack_encoded)
        zones_encoded = torch.Tensor(zones_encoded)
        player_states = torch.Tensor(player_states)

        # unpack the first dimension of the encoded zones to pass to FC layers
        zones_encoded = torch.flatten(zones_encoded, end_dim=0)

        out1 = nn.Softmax(self.fc1(torch.concat(zones_encoded, player_states)))
        out2 = nn.Softmax(self.fc2(out1))
        out3 = self.fc3(out2)

        value = self.value_out(out3)

        # out3 represents the model's understanding of the game state
        #   and its general decision making state

        # TODO: if we do like AlphaGo, we want to eventually predict reward?
        return out3


class ZoneEncoder(nn.Module):
    def __init__(self, weights=None):
        super().__init__()
        # TODO: determine how many features are in each game object -> input_size
        self.lstm = nn.LSTM(
            input_size=100, hidden_size=50, num_layers=2, batch_first=True, bidirectional=True
        )

    # Pre: any text in the game object has already been processed through a rules embedding
    def forward(self, x):
        r_out, (h_n, h_c) = self.lstm(x, None)
        # h_n gives us the 'final' hidden states for the forward and reverse pass
        # return h_n[0, :, :] + h_n[1, :, :]
        # unsure if it's necessary to flatten, we might want more dimensionality
        return torch.flatten(h_n, end_dim=0)


# class Rules_Embedding(nn.Module):
#     def __init__(self, weights=None):
#         super().__init__()
#         embedding_weights = None
#         self.embedding = nn.Embedding.from_pretrained(embedding_weights)
#         # fine-tuning only the final layer
#         self.embedding.requires_grad_(False)
#         # in features determined from embedding output
#         # keep the out_features the same?
#         self.fc = nn.Linear(in_features=10, out_features=10)
#
#     def forward(self, x):
#         pre_embed = self.embedding(x)
#         # any activation fn here?
#         return self.fc(x)
